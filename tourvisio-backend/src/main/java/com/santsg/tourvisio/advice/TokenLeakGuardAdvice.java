package com.santsg.tourvisio.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Guard‑rail that ensures the TourVisio API token never leaks in a response
 * payload.
 * It inspects the serialized JSON and removes any field named "apiToken" or
 * "token".
 * If such a field is found, a warning is logged and the field is stripped
 * before the
 * response is sent to the client.
 */
@Component
public class TokenLeakGuardAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger logger = LoggerFactory.getLogger(TokenLeakGuardAdvice.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        // Apply to all controller responses that produce JSON.
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        try {
            // Serialize to a mutable JSON node tree.
            ObjectNode node = mapper.valueToTree(body);
            boolean modified = false;
            if (node.has("apiToken")) {
                node.remove("apiToken");
                modified = true;
            }
            if (node.has("token")) {
                node.remove("token");
                modified = true;
            }
            if (modified) {
                logger.warn("Potential token field removed from response to prevent leakage: {} {}", request.getURI(),
                        body.getClass().getSimpleName());
                return mapper.treeToValue(node, body.getClass());
            }
        } catch (Exception e) {
            logger.error("TokenLeakGuardAdvice failed to process response body", e);
        }
        return body;
    }
}
