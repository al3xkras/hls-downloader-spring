package com.al3xkras.hls_downloader;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.bonigarcia.wdm.WebDriverManager;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class Main {
    private static final int URL_MAX_SIZE=200;
    private static final String URL_SEARCH="m3u8";
    private static final String HTTP="http";

    public static void main(String[] args) throws InterruptedException, IOException {

        //assert args.length>0;
        String webpageUrl = "http://localhost:10001";

        BrowserMobProxy proxy = new BrowserMobProxyServer();
        proxy.start(19091);
        proxy.setHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setCapability("proxy", seleniumProxy);
        options.addArguments("--ignore-certificate-errors");
        ChromeDriver chromeDriver = new ChromeDriver(options);

        proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);

        proxy.newHar("localhost");

        DevTools devTools = chromeDriver.getDevTools();
        devTools.createSession();
        devTools.send(new Command<>("Network.enable", ImmutableMap.of()));

        chromeDriver.get(webpageUrl);

        Thread.sleep(20000);
        Har har = proxy.getHar();
        proxy.stop();

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        har.writeTo(new PrintWriter(o));
        String s = o.toString(StandardCharsets.UTF_8);

        System.out.println("\n\n\n\n\n\n\n\n\n\n");
        int index;

        String videoUrl = null;

        while ((index=s.indexOf("m3u8"))>0){
            String url = s.substring(index-URL_MAX_SIZE,index+URL_SEARCH.length());
            s=s.substring(index+URL_SEARCH.length());

            int urlStart=s.indexOf(HTTP);
            String pre = url.substring(0,urlStart).toLowerCase();
            String actualUrl=url.substring(urlStart);
            videoUrl=actualUrl;
            System.out.println(actualUrl);
        }

        String filename="video1.mp4";
        String cmd = String.format("youtube-dl --all-subs -f mp4 -o \"%s\" \"%s\"",filename,videoUrl);

        Process youtubeDl = Runtime.getRuntime().exec(cmd);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(youtubeDl.getInputStream()) );
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);
        }
        in.close();
    }
}
