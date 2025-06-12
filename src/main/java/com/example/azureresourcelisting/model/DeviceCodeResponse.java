package com.example.azureresourcelisting.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DeviceCodeResponse {
    private String userCode;
    private String verificationUri;
    private String message;
}
