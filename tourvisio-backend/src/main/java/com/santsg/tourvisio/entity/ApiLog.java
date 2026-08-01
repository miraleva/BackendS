package com.santsg.tourvisio.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "api_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp")
    private String timestamp;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "uri", length = 1024)
    private String uri;

    @Column(name = "endpoint_type", length = 50)
    private String endpointType;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "status_code")
    private int statusCode;

    @Column(name = "status_text", length = 100)
    private String statusText;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "success")
    private boolean success;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;
}
