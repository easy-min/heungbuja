package com.heungbuja.common.config;

import jakarta.websocket.server.ServerContainer;
import org.apache.tomcat.websocket.server.WsServerContainer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketBufferConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(context -> {
            Object attr = context.getServletContext()
                    .getAttribute("javax.websocket.server.ServerContainer");

            if (attr instanceof WsServerContainer) {
                WsServerContainer container = (WsServerContainer) attr;

                // 텍스트/바이너리 WebSocket 메시지 버퍼 사이즈 확장 (기본은 8KB~64KB 수준)
                container.setDefaultMaxTextMessageBufferSize(1024 * 1024);    // 1MB
                container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024); // 1MB
            }
        });
    }
}
