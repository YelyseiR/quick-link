package ua.patronum.quicklink.auth.dto.registration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegistrationRequest {

    private String username;
    private String password;
    private String confirmPassword;
}