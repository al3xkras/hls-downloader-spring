package com.al3xkras.hls_downloader;

import com.browserup.bup.BrowserUpProxy;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.client.ClientUtil;
import com.browserup.bup.proxy.CaptureType;
import com.browserup.harreader.model.Har;
import com.google.common.collect.ImmutableMap;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public abstract class VideoDownloader {
    private VideoDownloader(){};

    private static final int URL_MIN_SIZE=20;
    private static final int URL_MAX_SIZE=500;
    private static final Pattern M3U8 =Pattern.compile("http.{"+URL_MIN_SIZE+","+URL_MAX_SIZE+"}m3u8");
    private static final Pattern MP4 =Pattern.compile("http.{"+URL_MIN_SIZE+","+URL_MAX_SIZE+"}mp4");
    private static final List<Pattern> URL_PATTERNS_PRIORITY = Arrays.asList(
            M3U8,
            MP4
    );
    private static final long CLICK_TIMEOUT = 1000L;
    private static final long SETUP_TIMEOUT = 60000;
    private static final int loops = 10;

    public static final Deque<Process> activeDownloads = new ConcurrentLinkedDeque<>();

    private static boolean isSetupRunning = false;
    private static boolean canDownload = true;
    private static final String seleniumProfile = Paths.get("").toAbsolutePath()+"/selenium";

    private static BrowserUpProxy setupProxy(){
        BrowserUpProxy proxy = new BrowserUpProxyServer();

        Set<CaptureType> captureTypes = new HashSet<>(Arrays.asList(
                CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT
        ));
        proxy.start(19091);
        proxy.setHarCaptureTypes(captureTypes);

        proxy.enableHarCaptureTypes(captureTypes);
        proxy.newHar("har0");

        return proxy;
    }

    private static ChromeDriver setupDriver(Proxy proxy, String profileDir, boolean headless){
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        if (proxy!=null)
            options.setCapability("proxy", proxy);
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-web-security");
        options.addArguments("--mute-audio");
        options.setCapability("acceptInsecureCerts",true);
        options.setExperimentalOption("excludeSwitches",Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension",false);


        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--start-maximized");
        //options.addExtensions(new File("./adblock.crx"));
        //options.addArguments("--disable-popup-blocking");
        if (headless)
            options.addArguments("--headless");
        if (profileDir!=null) {
            options.addArguments("--user-data-dir="+profileDir);
            options.addArguments("--profile-directory=Default");
        }

        ChromeDriver chromeDriver = new ChromeDriver(options);

        chromeDriver.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        chromeDriver.executeCdpCommand("Network.setUserAgentOverride",ImmutableMap.of(
                "userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.53 Safari/537.36"
        ));
        log.info(chromeDriver.executeScript("return navigator.userAgent;").toString());

        DevTools devTools = chromeDriver.getDevTools();
        devTools.createSession();
        devTools.send(new Command<>("Network.enable", ImmutableMap.of()));

        return chromeDriver;
    }

    public static void setupCookies(String webpageUrl){
        if (!canSetup()){
            throw new IllegalStateException("setup is unavailable");
        }

        BrowserUpProxy proxy =setupProxy();
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        ChromeDriver chromeDriver = setupDriver(seleniumProxy, seleniumProfile, false);

        chromeDriver.get(webpageUrl);
        String initialHandle = chromeDriver.getWindowHandle();
        long time0 = System.currentTimeMillis();
        isSetupRunning=true;
        try {
            while (isSetupRunning && System.currentTimeMillis()-time0 < SETUP_TIMEOUT) {
                for (String handle : chromeDriver.getWindowHandles()) {
                    if (handle.equals(initialHandle)) {
                        continue;
                    }
                    chromeDriver.switchTo().window(handle).close();
                }
            }
            chromeDriver.switchTo().window(initialHandle);
            chromeDriver.quit();
            log.info("cookie setup completed");
        } catch (RuntimeException e){
            log.error("setup",e);
            try {
                chromeDriver.close();
            } catch (RuntimeException r){
                log.error("setup",r);
            }
        }
        proxy.stop();
        isSetupRunning=false;
    }

    public static boolean canSetup(){
        return !isSetupRunning;
    }

    public static boolean canDownload(){
        return canDownload;
    }

    public static void completeSetup(){
        log.info("setting up cookies");
        isSetupRunning=false;
    }

    public static void deleteCookies() throws IOException {
        if (seleniumProfile!=null)
            FileUtils.deleteDirectory(new File(seleniumProfile));
    }

    public static void download(String[] args, BiConsumer<Process,BufferedReader> processOutput,
                                ChromeDriver driver, BrowserUpProxy pr) {
        if (!canDownload()){
            throw new IllegalStateException("downloads are currently unavailable");
        }
        canDownload=false;
        try {
            initiateDownload(args,processOutput,driver,pr);
        } catch (RuntimeException | IOException | InterruptedException e) {
            canDownload=true;
            throw new RuntimeException(e);
        }
        canDownload=true;
    }

    private static void initiateDownload(String[] args, BiConsumer<Process,BufferedReader> processOutput,
                                ChromeDriver driver, BrowserUpProxy pr)
            throws InterruptedException, IOException {

        assert args.length>=2;
        String webpageUrl = args[0];
        String filename=args[1];

        BrowserUpProxy proxy = pr==null?setupProxy():pr;
        Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
        ChromeDriver chromeDriver = driver==null?setupDriver(seleniumProxy, seleniumProfile, true):driver;

        chromeDriver.get(webpageUrl);
        String initialHandle = chromeDriver.getWindowHandle();

        WebDriverWait wait = new WebDriverWait(chromeDriver, Duration.ofMillis(CLICK_TIMEOUT));
        Actions actions = new Actions(chromeDriver);
        int i;
        for (i=0; i<loops || chromeDriver.getWindowHandles().size()>1; i++){
            actions.moveToElement(chromeDriver.findElement(By.tagName("body")), 0, 0);
            actions.moveByOffset(10, 10).click().build().perform();
            if (chromeDriver.getWindowHandles().size()>1){
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

        new WebDriverWait(chromeDriver, Duration.ofMillis((loops-i)* CLICK_TIMEOUT))
                .until(d->d.findElements(By.tagName("iframe")));

        try {
            WebElement iframe = chromeDriver.findElements(By.tagName("iframe")).get(0);

            String iframeSource = iframe.getAttribute("src");
            if (iframeSource != null)
                log.info(iframeSource);

            try {
                actions.moveToElement(iframe, 10, 25)
                        .click().build().perform();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }

            Thread.sleep((loops-i) * CLICK_TIMEOUT);
        } catch (IndexOutOfBoundsException e){
            log.error("iframe element not found. webpage: "+webpageUrl);
        }

        Har har = proxy.getHar();
        proxy.stop();
        canDownload=true;
        Thread.sleep(CLICK_TIMEOUT);

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
            chromeDriver.quit();
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
        chromeDriver.quit();
        assert youtubeDl.waitFor()==0;
        activeDownloads.remove(youtubeDl);
    }
}
