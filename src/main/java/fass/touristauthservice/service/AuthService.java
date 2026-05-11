package fass.touristauthservice.service;

import fass.touristauthservice.domain.event.TouristRegisteredEvent;
import fass.touristauthservice.domain.model.AccountStatus;
import fass.touristauthservice.domain.model.ActivationToken;
import fass.touristauthservice.domain.model.Tourist;
import fass.touristauthservice.domain.repository.ActivationTokenRepository;
import fass.touristauthservice.domain.repository.TouristRepository;
import fass.touristauthservice.exception.BadRequestException;
import fass.touristauthservice.exception.ConflictException;
import fass.touristauthservice.exception.UnauthorizedException;
import fass.touristauthservice.dto.request.LoginRequest;
import fass.touristauthservice.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final TouristRepository touristRepo;
    private final ActivationTokenRepository tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void register(RegisterRequest req) {
        if (touristRepo.existsByEmail(req.email())) {
            throw new ConflictException("Email already taken");
        }

        Tourist tourist = new Tourist();
        tourist.setEmail(req.email());
        tourist.setName(req.name());
        tourist.setPasswordHash(passwordEncoder.encode(req.password()));
        tourist.setStatus(AccountStatus.PENDING);
        touristRepo.save(tourist);

        ActivationToken token = new ActivationToken();
        token.setTouristId(tourist.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setUsed(false);
        tokenRepo.save(token);

        kafkaTemplate.send("tourist.registered",
                objectMapper.writeValueAsString(new TouristRegisteredEvent(
                tourist.getId(),
                tourist.getEmail(),
                tourist.getName(),
                token.getToken()
        )));
    }

    public void activate(String tokenValue) {
        ActivationToken token = tokenRepo.findByToken(tokenValue)
                .orElseThrow(() -> new BadRequestException("Invalid activation token"));

        if (token.isUsed()) {
            throw new BadRequestException("Token already used");
        }

        Tourist tourist = touristRepo.findById(token.getTouristId())
                .orElseThrow(() -> new BadRequestException("Tourist not found"));

        tourist.setStatus(AccountStatus.ACTIVE);
        token.setUsed(true);
    }

    public void resendActivation(String email) {
        Tourist tourist = touristRepo.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Email not found"));

        if (tourist.getStatus() == AccountStatus.ACTIVE) {
            throw new ConflictException("Account already active");
        }

        tokenRepo.markAllUnusedAsUsed(tourist.getId());
        ActivationToken newToken = new ActivationToken();
        newToken.setTouristId(tourist.getId());
        newToken.setToken(UUID.randomUUID().toString());
        newToken.setUsed(false);
        tokenRepo.save(newToken);

        kafkaTemplate.send("tourist.registered",
                objectMapper.writeValueAsString(new TouristRegisteredEvent(
                tourist.getId(),
                tourist.getEmail(),
                tourist.getName(),
                newToken.getToken()
        )));
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest req) {
        Tourist tourist = touristRepo.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedException("Bad credentials"));

        if (tourist.getStatus() != AccountStatus.ACTIVE) {
            throw new UnauthorizedException("Account not activated");
        }

        if (!passwordEncoder.matches(req.password(), tourist.getPasswordHash())) {
            throw new UnauthorizedException("Bad credentials");
        }

        return jwtService.generate(tourist.getId(), "TOURIST");
    }
}
