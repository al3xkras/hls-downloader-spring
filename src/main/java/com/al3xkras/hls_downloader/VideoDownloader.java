package com.al3xkras.hls_downloader;

import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.client.ClientUtil;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.harreader.model.Har;
import com.google.common.collect.ImmutableMap;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class VideoDownloader {
    private static final int URL_MIN_SIZE=20;
    private static final int URL_MAX_SIZE=500;
    private static final Pattern M3U8 =Pattern.compile("http.{"+URL_MIN_SIZE+","+URL_MAX_SIZE+"}m3u8");
    private static final Pattern MP4 =Pattern.compile("http.{"+URL_MIN_SIZE+","+URL_MAX_SIZE+"}mp4");
    private static final List<Pattern> URL_PATTERNS_PRIORITY = Arrays.asList(
            M3U8,
            MP4
    );
    private static final long timeout = 1000L;
    private static final int loops = 10;

    public static final Deque<Process> activeDownloads = new ConcurrentLinkedDeque<>();

    public static void download(
            String[] args, BiConsumer<Process,BufferedReader> processOutput)
            throws InterruptedException, IOException {

        assert args.length>=2;
        String webpageUrl = args[0];
        String filename=args[1];

        BrowserUpProxy proxy = new BrowserUpProxyServer();

        Set<CaptureType> captureTypes = new HashSet<>(Arrays.asList(
                CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT
        ));
        proxy.start(19091);
        proxy.setHarCaptureTypes(captureTypes);
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);

        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.setCapability("proxy", seleniumProxy);
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--silent");
        options.addArguments("--mute-audio");
        //options.addExtensions(new File("./adblock.crx"));
        //options.addArguments("--disable-popup-blocking");
        options.addArguments("--headless");
        ChromeDriver chromeDriver = new ChromeDriver(options);

        proxy.enableHarCaptureTypes(captureTypes);

        proxy.newHar("har0");

        DevTools devTools = chromeDriver.getDevTools();
        devTools.createSession();
        devTools.send(new Command<>("Network.enable", ImmutableMap.of()));

        chromeDriver.get(webpageUrl);


        WebDriverWait wait = new WebDriverWait(chromeDriver, Duration.ofMillis(timeout));
        Actions actions = new Actions(chromeDriver);
        int i;
        for (i=0; i<loops || chromeDriver.getWindowHandles().size()>1; i++){
            actions.moveToElement(chromeDriver.findElement(By.tagName("body")), 0, 0);
            actions.moveByOffset(10, 10).click().build().perform();
            if (chromeDriver.getWindowHandles().size()>1){
                String initialHandle = chromeDriver.getWindowHandle();
                for (String handle:chromeDriver.getWindowHandles()){
                    if (handle.equals(initialHandle)){
                        continue;
                    }
                    chromeDriver.switchTo().window(handle).close();
                }
                chromeDriver.switchTo().window(initialHandle);
            }
            try {
                wait.until(ExpectedConditions.elementToBeClickable(By.tagName("iframe")));
                break;
            } catch (RuntimeException ignored){}
        }

        new WebDriverWait(chromeDriver, Duration.ofMillis(loops*timeout))
                .until(d->d.findElements(By.tagName("iframe")));

        WebElement el= chromeDriver.findElements(By.tagName("iframe")).get(0);

        try {
            actions.moveToElement(el, 10, 25)
                    .click().build().perform();
        } catch (RuntimeException e){
            e.printStackTrace();
        }

        Thread.sleep((loops-i) * timeout);

        Har har = proxy.getHar();
        proxy.stop();
        Thread.sleep(timeout);

        ByteArrayOutputStream o = new ByteArrayOutputStream();
        har.writeTo(new PrintWriter(o));
        String s = o.toString(StandardCharsets.UTF_8);

        List<String> rawUrls = new LinkedList<>();

        for (Pattern pattern : URL_PATTERNS_PRIORITY){
            Matcher urlMatcher = pattern.matcher(s);
            while (urlMatcher.find()){
                rawUrls.add(urlMatcher.group());
            }
            if (rawUrls.size()>0)
                break;
        }

        List<URI> videoUrls = rawUrls.stream().map(x->{
            try{
                return URI.create(x);
            } catch (RuntimeException r){
                return null;
            }
        }).filter(Objects::nonNull).toList();

        log.info(rawUrls.toString());
        log.info(videoUrls.toString());

        String videoUrl;

        if (webpageUrl.contains("youtube.com")){
            videoUrl=webpageUrl;
        } else if (!videoUrls.isEmpty()){
            videoUrl=videoUrls.get(videoUrls.size()-1).toString();
        } else {
            log.error("unable to download: no video source found.");
            return;
        }

        ProcessBuilder pb = new ProcessBuilder("youtube-dl",
                "--all-subs" , "-f" , "mp4", "-o" , "\""+filename+"\"" ,
                "\""+videoUrl+"\"");

        pb.directory(new File(System.getProperty("user.dir")));

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process youtubeDl = pb.start();
        activeDownloads.add(youtubeDl);
        for (String handle:chromeDriver.getWindowHandles()){
            chromeDriver.switchTo().window(handle).close();
        }
        assert youtubeDl.waitFor()==0;
        activeDownloads.remove(youtubeDl);
    }
}
