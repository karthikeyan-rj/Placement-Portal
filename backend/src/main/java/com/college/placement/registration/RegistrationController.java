package com.college.placement.registration;

import com.college.placement.registration.dto.CompleteRegistrationRequest;
import com.college.placement.registration.dto.CompleteRegistrationResponse;
import com.college.placement.registration.dto.RequestCodeRequest;
import com.college.placement.registration.dto.RequestCodeResponse;
import com.college.placement.registration.dto.VerifyCodeRequest;
import com.college.placement.registration.dto.VerifyCodeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/request-code")
    public ResponseEntity<RequestCodeResponse> requestCode(@Valid @RequestBody RequestCodeRequest request) {
        RequestCodeResponse response = registrationService.requestCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-code")
    public ResponseEntity<VerifyCodeResponse> verifyCode(@Valid @RequestBody VerifyCodeRequest request) {
        VerifyCodeResponse response = registrationService.verifyCode(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/complete")
    public ResponseEntity<CompleteRegistrationResponse> completeRegistration(@Valid @RequestBody CompleteRegistrationRequest request) {
        CompleteRegistrationResponse response = registrationService.completeRegistration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
