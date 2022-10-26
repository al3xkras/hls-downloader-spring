package com.al3xkras.hls_downloader.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoDTO implements Serializable {
    private String url;
    private String filename;
    private Boolean isframe;
}
