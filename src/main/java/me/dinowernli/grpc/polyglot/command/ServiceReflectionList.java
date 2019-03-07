package me.dinowernli.grpc.polyglot.command;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;
import com.google.protobuf.DescriptorProtos;
import io.grpc.Channel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import me.dinowernli.grpc.polyglot.grpc.ChannelFactory;
import me.dinowernli.grpc.polyglot.grpc.ServerReflectionClient;
import me.dinowernli.grpc.polyglot.io.Output;
import me.dinowernli.grpc.polyglot.oauth2.OauthCredentialsFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polyglot.ConfigProto;

import java.util.Optional;

public class ServiceReflectionList {
    private static final Logger logger = LoggerFactory.getLogger(ServiceReflectionList.class);

    /** Calls the endpoint specified in the arguments */
    public static void callEndpoint(
            Output output,
            Optional<String> endpoint,
            ConfigProto.CallConfiguration callConfig) {
        Preconditions.checkState(endpoint.isPresent(), "--endpoint argument required");

        HostAndPort hostAndPort = HostAndPort.fromString(endpoint.get());
        ChannelFactory channelFactory = ChannelFactory.create(callConfig);

        logger.info("Creating channel to: " + hostAndPort.toString());
        Channel channel;
        if (callConfig.hasOauthConfig()) {
            channel = channelFactory.createChannelWithCredentials(
                    hostAndPort, new OauthCredentialsFactory(callConfig.getOauthConfig()).getCredentials());
        } else {
            channel = channelFactory.createChannel(hostAndPort);
        }
        // Add white-space before the rendered output
        output.newLine();
        resolveServiceByReflection(output, channel);
    }

    /**
     * Returns a {@link DescriptorProtos.FileDescriptorSet} describing the supplied service if the remote server
     * advertizes it by reflection. Returns an empty optional if the remote server doesn't support
     * reflection. Throws a NOT_FOUND exception if we determine that the remote server does not
     * support the requested service (but *does* support the reflection service).
     */
    private static void resolveServiceByReflection(Output output,
                                                   Channel channel) {
        ServerReflectionClient serverReflectionClient = ServerReflectionClient.create(channel);
        ImmutableList<String> services;
        try {
            services = serverReflectionClient.listServices().get();
            output.writeLine("Full list of services available at this host and port");
            services.forEach(output::writeLine);
            // Add white-space before the rendered output
            output.newLine();
            for (String s:services){
                output.newLine();
                output.writeLine("Detailed methods information for:"+s);
                //serverReflectionClient.lookupService(s).get().getFileList().forEach(f->f.getServiceList().forEach(ss->ss.getMethodList().forEach(System.out::println)));
                output.writeLine(serverReflectionClient.lookupService(s).get().toString());
            }
        } catch (Throwable t) {
            // Listing services failed, try and provide an explanation.
            Throwable root = Throwables.getRootCause(t);
            if (root instanceof StatusRuntimeException) {
                if (((StatusRuntimeException) root).getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                    logger.error("Could not list services because the remote host does not support " +
                            "reflection. To disable resolving services by reflection, either pass the flag " +
                            "--use_reflection=false or disable reflection in your config file.");
                } else {
                    logger.error("You have some issues with the connection. When trying to get the service description through reflection, check the security(tls) .."+root.getMessage());
                    System.exit(-1);
                }
            }

        }
    }

}
