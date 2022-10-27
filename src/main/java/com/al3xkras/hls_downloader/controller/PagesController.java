package com.al3xkras.hls_downloader.controller;

import com.al3xkras.hls_downloader.HlsDownloaderApplication;
import com.al3xkras.hls_downloader.VideoDownloader;
import com.al3xkras.hls_downloader.config.ShutdownManager;
import com.al3xkras.hls_downloader.dto.VideoDTO;
import com.al3xkras.hls_downloader.event.HlsDownloadEvent;
import com.al3xkras.hls_downloader.event.QuitEvent;
import com.al3xkras.hls_downloader.model.CustomConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
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
    CustomConsumer readerConsumer;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    ShutdownManager manager;

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

        if (event.isCompleted()){
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Thread t = new Thread(()-> VideoDownloader.download(
                new String[]{event.getMainUrl(),event.getFilename()},
                readerConsumer,
                null,
                null
        ));
        t.start();
        return "splash";
    }

    @EventListener(classes = QuitEvent.class)
    public void quit(QuitEvent event){
        new Thread(()->{
            long start = System.currentTimeMillis();
            if (!event.isForceStop()) {
                while (System.currentTimeMillis() - start < event.getShutdownTimeout()) {
                    if (VideoDownloader.activeDownloads.isEmpty()) {
                        break;
                    }
                }
            }
            VideoDownloader.activeDownloads.forEach(process -> {
                if (HlsDownloaderApplication.os.contains("win")){
                    try {
                        Runtime.getRuntime().exec("taskkill /PID "+process.pid()+" /F");
                    } catch (IOException e) {
                        e.printStackTrace();
                        process.destroy();
                    }
                } else {
                    process.destroy();
                }
            });
            manager.initiateShutdown(0);
        }).start();
    }

    @PostMapping("/quit")
    @ResponseStatus(HttpStatus.OK)
    public void quitEndpoint(@RequestParam(value = "force",required = false) Boolean force,
                             @RequestParam(value = "timeout",required = false) Integer timeoutMillis){
        boolean forceStop = force != null && force;
        int shutdownTimeout = timeoutMillis==null?120000:timeoutMillis;
        publisher.publishEvent(new QuitEvent(manager,forceStop,shutdownTimeout));
    }
}
