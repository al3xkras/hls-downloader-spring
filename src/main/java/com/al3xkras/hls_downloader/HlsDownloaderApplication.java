package com.al3xkras.hls_downloader;

import com.al3xkras.hls_downloader.model.CustomConsumer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
@SpringBootApplication
public class HlsDownloaderApplication {
	public static void main(String[] args) throws IOException {

		SpringApplication.run(HlsDownloaderApplication.class, args);

		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")){
			Runtime rt = Runtime.getRuntime();
			String url = "http://localhost:10001/";
			rt.exec("rundll32 url.dll,FileProtocolHandler " + url);
		}
	}

	@Bean
	public CustomConsumer readerConsumer(){
		return new CustomConsumer();
	}
}
