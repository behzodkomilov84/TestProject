package behzoddev.testproject.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@EnableSpringDataWebSupport(
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final OnlineUserTrackingInterceptor onlineUserTrackingInterceptor;

    // Foydalanuvchi "🟢 Onlayn" holatini kuzatish — /users sahifasida
    // ko'rsatish uchun (foydalanuvchi so'rovi, 2026-09-09).
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(onlineUserTrackingInterceptor);
    }

    // Savol/javob rasmlari (masalan, geometrik chizmalar) shu papkaga
    // saqlanadi (ko'ring FileStorageService) va shu orqali brauzerga
    // "/uploads/..." manzili bilan qaytariladi.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Path.of(uploadDir).toAbsolutePath().normalize().toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
