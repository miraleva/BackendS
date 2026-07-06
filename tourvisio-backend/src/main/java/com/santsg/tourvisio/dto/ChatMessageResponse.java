package com.santsg.tourvisio.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String intent;
    private List<String> missingFields;
    private String botMessage;
    private String chatStatus;
}
