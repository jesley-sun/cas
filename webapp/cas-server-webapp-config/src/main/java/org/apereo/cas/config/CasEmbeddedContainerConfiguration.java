package org.apereo.cas.config;

import com.google.common.base.Throwables;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.valves.ExtendedAccessLogValve;
import org.apache.catalina.valves.rewrite.RewriteValve;
import org.apache.commons.lang3.StringUtils;
import org.apache.coyote.AbstractProtocol;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.SocketUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * This is {@link CasEmbeddedContainerConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("casEmbeddedContainerConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class CasEmbeddedContainerConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(CasEmbeddedContainerConfiguration.class);

    @Autowired
    private ServerProperties serverProperties;

    @Value("${server.tomcat.valve.rewrite.config:classpath:/container/tomcat/rewrite.config}")
    private Resource rewriteValveConfig;

    @Autowired
    private CasConfigurationProperties casProperties;

    @ConditionalOnClass(Tomcat.class)
    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
        final TomcatEmbeddedServletContainerFactory tomcat =
                new TomcatEmbeddedServletContainerFactory();

        final org.apereo.cas.configuration.model.core.ServerProperties.Ajp ajp = casProperties.getServer().getAjp();
        if (ajp.isEnabled()) {
            LOGGER.debug("Creating AJP configuration for the embedded tomcat container...");
            final Connector ajpConnector = new Connector(ajp.getProtocol());
            ajpConnector.setProtocol(ajp.getProtocol());
            ajpConnector.setPort(ajp.getPort());
            ajpConnector.setSecure(ajp.isSecure());
            ajpConnector.setAllowTrace(ajp.isAllowTrace());
            ajpConnector.setScheme(ajp.getScheme());
            ajpConnector.setAsyncTimeout(ajp.getAsyncTimeout());
            ajpConnector.setEnableLookups(ajp.isEnableLookups());
            ajpConnector.setMaxPostSize(ajp.getMaxPostSize());

            if (ajp.getProxyPort() > 0) {
                LOGGER.debug("Set AJP proxy port to {}", ajp.getProxyPort());
                ajpConnector.setProxyPort(ajp.getProxyPort());
            }

            if (ajp.getRedirectPort() > 0) {
                LOGGER.debug("Set AJP redirect port to {}", ajp.getRedirectPort());
                ajpConnector.setRedirectPort(ajp.getRedirectPort());
            }
            tomcat.addAdditionalTomcatConnectors(ajpConnector);
        }

        if (casProperties.getServer().getHttp().isEnabled()) {
            LOGGER.debug("Creating HTTP configuration for the embedded tomcat container...");
            final Connector connector = new Connector(casProperties.getServer().getHttp().getProtocol());

            int port = casProperties.getServer().getHttp().getPort();
            if (port <= 0) {
                port = SocketUtils.findAvailableTcpPort();
            }
            LOGGER.debug("Set HTTP post to {}", port);
            connector.setPort(port);
            tomcat.addAdditionalTomcatConnectors(connector);
        }

        tomcat.getAdditionalTomcatConnectors()
                .stream()
                .filter(connector -> connector.getProtocolHandler() instanceof AbstractProtocol)
                .forEach(connector -> {
                    final AbstractProtocol handler = (AbstractProtocol) connector.getProtocolHandler();
                    handler.setSoTimeout(casProperties.getServer().getConnectionTimeout());
                    handler.setConnectionTimeout(casProperties.getServer().getConnectionTimeout());
                });

        if (casProperties.getServer().getExtAccessLog().isEnabled()
            && StringUtils.isNotBlank(casProperties.getServer().getExtAccessLog().getPattern())) {

            LOGGER.debug("Creating extended access log valve configuration for the embedded tomcat container...");
            final ExtendedAccessLogValve valve = new ExtendedAccessLogValve();
            valve.setPattern(casProperties.getServer().getExtAccessLog().getPattern());

            if (StringUtils.isBlank(casProperties.getServer().getExtAccessLog().getDirectory())) {
                valve.setDirectory(serverProperties.getTomcat().getAccesslog().getDirectory());
            } else {
                valve.setDirectory(casProperties.getServer().getExtAccessLog().getDirectory());
            }
            valve.setPrefix(casProperties.getServer().getExtAccessLog().getPrefix());
            valve.setSuffix(casProperties.getServer().getExtAccessLog().getSuffix());
            valve.setAsyncSupported(true);
            valve.setEnabled(true);
            valve.setRotatable(true);
            valve.setBuffered(true);
            tomcat.addContextValves(valve);
            tomcat.addEngineValves(valve);
        }

        if (StringUtils.isBlank(serverProperties.getContextPath())) {
            final RewriteValve valve = new RewriteValve() {
                @Override
                protected synchronized void startInternal() throws LifecycleException {
                    super.startInternal();
                    try (InputStream is = rewriteValveConfig.getInputStream();
                         InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                         BufferedReader buffer = new BufferedReader(isr)) {
                        parse(buffer);
                    } catch (final Exception e) {
                        throw Throwables.propagate(e);
                    }
                }
            };
            valve.setAsyncSupported(true);
            valve.setEnabled(true);

            LOGGER.debug("Creating Rewrite valve configuration for the embedded tomcat container...");
            tomcat.addContextValves(valve);
        }
        return tomcat;
    }
}
