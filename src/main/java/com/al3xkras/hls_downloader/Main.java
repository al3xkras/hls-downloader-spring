package com.al3xkras.hls_downloader;

import com.google.common.collect.ImmutableMap;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiConsumer;

@Slf4j
public class Main {
    private static final int URL_MAX_SIZE=200;
    private static final String URL_SEARCH="m3u8";
    private static final String HTTP="http";

    public static void download(
            String[] args, BiConsumer<Process,BufferedReader> processOutput)
            throws InterruptedException, IOException {

        assert args.length>=2;
        String webpageUrl = args[0];
        String filename=args[1];

        BrowserMobProxy proxy = new BrowserMobProxyServer();

        proxy.start(19091);
        proxy.setHarCaptureTypes(CaptureType.RESPONSE_CONTENT);
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setCapability("proxy", seleniumProxy);
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--headless");
        ChromeDriver chromeDriver = new ChromeDriver(options);

        proxy.enableHarCaptureTypes(CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);

        proxy.newHar("har0");

        DevTools devTools = chromeDriver.getDevTools();
        devTools.createSession();
        devTools.send(new Command<>("Network.enable", ImmutableMap.of()));

        chromeDriver.get(webpageUrl);

        new WebDriverWait(chromeDriver, Duration.ofMillis(30000))
                .until(d->d.findElements(By.tagName("iframe")));

        WebElement el= chromeDriver.findElements(By.tagName("iframe")).get(0);

        Actions builder = new Actions(chromeDriver);
        builder.moveToElement(el, 10, 25)
                .click().build().perform();

        Thread.sleep(10000);

        Har har = proxy.getHar();
        proxy.stop();
        Thread.sleep(1000);

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        har.writeTo(new PrintWriter(o));
        String s = o.toString(StandardCharsets.UTF_8);

        int index;

        String[] videoUrl = new String[]{null};

        while ((index=s.indexOf(URL_SEARCH))>0){
            String url = s.substring(index-URL_MAX_SIZE,index+URL_SEARCH.length());
            s=s.substring(index+URL_SEARCH.length());

            int urlStart=url.indexOf(HTTP);
            if (urlStart<0){
                System.err.println(url);
                continue;
            }
            String pre = url.substring(0,urlStart).toLowerCase();
            String actualUrl=url.substring(urlStart);
            videoUrl[0]=actualUrl;
            System.out.println(actualUrl);
        }


        ProcessBuilder pb = new ProcessBuilder("youtube-dl",
                "--all-subs" , "-f" , "mp4", "-o" , "\""+filename+"\"" ,
                "\""+videoUrl[0]+"\"");

        pb.directory(new File(System.getProperty("user.dir")));

        //File tempFile = File.createTempFile("hls", "-log");
        //pb.redirectOutput(ProcessBuilder.Redirect.to(tempFile));
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process youtubeDl = pb.start();

        //BufferedReader reader = new BufferedReader(new FileReader(tempFile));
        //processOutput.accept(youtubeDl,reader);

        chromeDriver.close();
        assert youtubeDl.waitFor()==0;
    }
}
