package org.example.importexportservice.token;

import lombok.RequiredArgsConstructor;
import org.example.importexportservice.service.TokenService;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class TokenHolder {

    private static final Logger log = LoggerFactory.getLogger(TokenHolder.class);
    private final TokenService tokenService;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final CountDownLatch tokenReady = new CountDownLatch(1);


    public String getToken(){
      try {
          tokenReady.await();
      }catch (InterruptedException e){
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Token kutishda xatolik");
      }
      return accessToken.get();
    }

    /**
     * Har 9 minutda tokenni yangilaydi (10 minutlik token uchun xavfsiz vaqt)
     * 9 min = 9 * 60 * 1000 = 540,000 ms
     */

    @Scheduled(fixedRate = 540_000)
    public void refresh() {
        try {
            log.info("Token keshga yangilash boshlandi...");
            String newToken = tokenService.getAccessToken();
            accessToken.set(newToken);
            tokenReady.countDown();
            log.info("Token keshda muvaffaqiyatli yangilandi.");
        }catch (Exception e){
            log.error("Tokenni yangilashda xatolik yuz berdi: {}", e.getMessage());
        }

    }


}
