package com.al3xkras.hls_downloader.controller;

import com.al3xkras.hls_downloader.Main;
import com.al3xkras.hls_downloader.dto.VideoDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Controller
public class PagesController {
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
    @ResponseStatus(HttpStatus.OK)
    public String process(@ModelAttribute VideoDTO videoDTO,
                          HttpServletRequest request){
        String webpageUrl = videoDTO.getUrl();
        String filename=videoDTO.getFilename();
        String mainUrl = "http://localhost:10001"+request.getRequestURI()+"?videoUrl="+webpageUrl;
        new Thread(()->{
            try {
                Main.main(new String[]{mainUrl,filename});
            } catch (InterruptedException | IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
        return "redirect:"+mainUrl;
    }
}
