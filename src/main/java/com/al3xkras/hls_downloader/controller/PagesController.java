package com.al3xkras.hls_downloader.controller;

import com.al3xkras.hls_downloader.Main;
import com.al3xkras.hls_downloader.dto.VideoDTO;
import com.al3xkras.hls_downloader.event.HlsDownloadEvent;
import com.al3xkras.hls_downloader.model.CustomConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Controller
public class PagesController {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    CustomConsumer readerConsumer;

    @Resource(name = "downloadEvent")
    List<HlsDownloadEvent> hlsDownloadEvents;

    @ExceptionHandler(AssertionError.class)
    public ResponseEntity<String> handleAssertions(){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("assertion error");
    }

    @Bean
    @SessionScope
    public List<HlsDownloadEvent> downloadEvent(){
        log.warn("created");
        return new LinkedList<>(Collections.singletonList(null));
    }

    @GetMapping("/")
    public String index(Model model){
        model.addAttribute("videoDTO",new VideoDTO());
        return "index";
    }
    @GetMapping("/process")
    public String processVideo(@RequestParam("videoUrl") String videoUrl, Model model){
        model.addAttribute("videoUrl",videoUrl);
        return "process";
    }

    @PostMapping("/process")
    public String process(@ModelAttribute VideoDTO videoDTO,
                          HttpServletRequest request){
        String webpageUrl = videoDTO.getUrl();
        String filename=videoDTO.getFilename();
        String mainUrl;
        if (videoDTO.getIsframe()){
            mainUrl="http://localhost:10001"+request.getRequestURI()+"?videoUrl="+webpageUrl;
        } else {
            mainUrl=webpageUrl;
        }
        hlsDownloadEvents.set(0, new HlsDownloadEvent(webpageUrl, filename, mainUrl));
        return "redirect:/splash";
    }

    @GetMapping("/splash")
    public String splashscreen(){
        log.warn(hlsDownloadEvents.toString());
        HlsDownloadEvent event = hlsDownloadEvents.get(0);
        if (event==null)
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        log.info(event.toString());

        assert !event.isCompleted();

        Thread t = new Thread(()->{
            try {
                Main.download(new String[]{event.getMainUrl(),event.getFilename()},readerConsumer);
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return "splash";
    }
}
