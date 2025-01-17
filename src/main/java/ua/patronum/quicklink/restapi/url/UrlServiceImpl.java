package ua.patronum.quicklink.restapi.url;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.patronum.quicklink.data.entity.Url;
import ua.patronum.quicklink.data.entity.User;
import ua.patronum.quicklink.data.repository.UrlRepository;
import ua.patronum.quicklink.restapi.auth.UserService;

import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private static final int SHORT_URL_LENGTH = 8;
    private static final String URL_PREFIX = "https://";

    private final UrlRepository urlRepository;
    private final UserService userService;
    private List<Url> userUrls;
    private final LocalDateTime currentTime = LocalDateTime.now();

    @Override
    public GetAllUrlsResponse getAllUrls() {
        List<Url> allUrls = urlRepository.findAll();
        return GetAllUrlsResponse.success(filterActivesUrlsTest(allUrls, false));
    }

    @Override
    public GetAllUserUrlsResponse getAllUserUrls(String username) {
        User user = userService.findByUsername(username);
        userUrls = urlRepository.findByUser(user);
        return GetAllUserUrlsResponse.success(filterActivesUrlsTest(userUrls, false));
    }

    @Override
    public GetAllUserActiveUrlsResponse getAllUserActiveUrl(String username) {
        User user = userService.findByUsername(username);
        userUrls = urlRepository.findByUser(user);
        return GetAllUserActiveUrlsResponse.success(filterActivesUrlsTest(userUrls, true));
    }

    @Override
    public CreateUrlResponse createUrl(String username, CreateUrlRequest request) {

        User user = userService.findByUsername(username);
        Optional<Error> validationError = validateCreateFields(request);

        if (validationError.isPresent()) {
            return CreateUrlResponse.failed(validationError.get());
        }

        if (!isValidUrl(request.getOriginalUrl())) {
            return CreateUrlResponse.failed(Error.INVALID_ORIGINAL_URL);
        }

        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortUrl(generateShortUrl(SHORT_URL_LENGTH))
                .dateCreated(LocalDateTime.now())
                .visitCount(0)
                .user(user)
                .build();
        url.setExpirationDate();
        urlRepository.save(url);

        return CreateUrlResponse.success();
    }

    @Override
    public DeleteUrlResponse deleteUrlById(String username, Long id) {
        Optional<Url> optionalUrl = urlRepository.findById(id);

        if (optionalUrl.isEmpty()) {
            return DeleteUrlResponse.failed(Error.INVALID_ID);
        }
        Url url = optionalUrl.get();

        if (!url.getUser().getUsername().equals(username)) {
            return DeleteUrlResponse.failed(Error.INVALID_ACCESS);
        }
        urlRepository.deleteById(id);
        return DeleteUrlResponse.success();
    }

    public String generateShortUrl(int length) {
        return URL_PREFIX + UUID.randomUUID().toString().subSequence(0, length);
    }

    private Optional<Error> validateCreateFields(CreateUrlRequest request) {
        if (Objects.isNull(request.getOriginalUrl()) || request.getOriginalUrl().isEmpty()) {
            return Optional.of(Error.EMPTY_NEW_URL);
        }
        return Optional.empty();
    }

    private boolean isValidUrl(String inputUrl) {
        try {
            if (!inputUrl.startsWith(URL_PREFIX)) {
                inputUrl = URL_PREFIX + inputUrl;
            }
            new URI(inputUrl);
            int statusCode = getStatusCode(inputUrl);
            return 200 <= statusCode && statusCode <= 204;
        } catch (Exception ignore) {
            return false;
        }
    }

    private int getStatusCode(String inputUrl) {
        try {
            URI url = new URI(inputUrl);
            HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode;
        } catch (IOException | URISyntaxException ignore) {
            return -1;
        }
    }

    @Override
    public RedirectResponse redirectOriginalUrl(RedirectRequest request) {
        Optional<Url> optionalUrl = urlRepository.findByShortUrl(request.getShortUrl());

        if (optionalUrl.isEmpty()) {
            return RedirectResponse.failed(Error.INVALID_SHORT_URL);
        }
        Url urls = optionalUrl.get();

        if (currentTime.isAfter(urls.getExpirationDate())) {
            return RedirectResponse.failed(Error.TIME_NOT_PASSED);
        }
        urls.incrementVisitCount();
        urlRepository.save(urls);
        return RedirectResponse.success(urls.getOriginalUrl());
    }

    @Override
    public GetAllActiveUrlsResponse getAllActiveUrls() {
        List<Url> allUrls = urlRepository.findAll();
        return GetAllActiveUrlsResponse.success(filterActivesUrlsTest(allUrls, true));
    }

    @Override
    public ExtensionTimeResponse getExtensionTime(ExtensionTimeRequest request) {
        Optional<Url> optionalUrl = urlRepository.findByShortUrl(request.getShortUrl());

        if (optionalUrl.isEmpty()) {
            return ExtensionTimeResponse.failed(Error.INVALID_SHORT_URL);
        }
        Url url = optionalUrl.get();

        if (currentTime.isBefore(url.getExpirationDate())) {
            return ExtensionTimeResponse.failed(Error.TIME_NOT_PASSED);
        }

        url.setExpirationDate(currentTime.plusDays(30));
        urlRepository.save(url);

        return ExtensionTimeResponse.success();
    }

    private List<UrlDto> filterActivesUrlsTest(List<Url> allUrls, boolean isActives) {
        return allUrls.stream()
                .filter(url -> !isActives || url.getExpirationDate() == null 
                        || url.getExpirationDate().isAfter(LocalDateTime.now()))
                .map(url -> UrlDto.builder()
                        .id(url.getId())
                        .originalUrl(url.getOriginalUrl())
                        .shortUrl(url.getShortUrl())
                        .dateCreated(url.getDateCreated())
                        .expirationDate(url.getExpirationDate())
                        .visitCount(url.getVisitCount())
                        .username(url.getUser().getUsername())
                        .build())
                .toList();
    }
}
