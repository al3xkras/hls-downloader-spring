package com.al3xkras.hls_downloader.controller;

import com.al3xkras.hls_downloader.VideoDownloader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;


@Slf4j
@RestController
@RequestMapping("/setup")
public class SetupController {

    @PostMapping("/cookies")
    @ResponseStatus(HttpStatus.OK)
    public void cookieSetup(
            @RequestParam(value = "webpage-url", required = false) String webpageUrl,
            @RequestParam(value = "complete-setup",required = false) Boolean completeSetup,
            @RequestParam(value = "delete", required = false) Boolean delete){

        if (completeSetup!=null){
            if (completeSetup){
                if (VideoDownloader.canSetup()){
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                }
                log.info("cookie setup completed");
                VideoDownloader.completeSetup();
                return;
            }
            assert webpageUrl!=null;
            new Thread(()-> VideoDownloader.setupCookies(webpageUrl)).start();
        } else if (delete!=null){
            try {
                VideoDownloader.deleteCookies();
            } catch (IOException e){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"google profile not found");
            }
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
