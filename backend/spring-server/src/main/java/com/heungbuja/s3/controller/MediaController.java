package com.heungbuja.s3.controller;

import com.heungbuja.s3.service.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaUrlService mediaUrlService;

    // 단일 파일용 (노래/영상)
    @GetMapping("/{id}/url")
    public Map<String, String> getUrl(@PathVariable long id) {
        String url = mediaUrlService.issueUrlById(id);
        return Map.of("url", url);
    }

    // 음악 + 영상 쌍 URL 반환 (동시 재생용)
    @GetMapping("/pair/url")
    public Map<String, String> getPairUrl(@RequestParam long musicId,
                                          @RequestParam long videoId) {
        String musicUrl = mediaUrlService.issueUrlById(musicId);
        String videoUrl = mediaUrlService.issueUrlById(videoId);
        return Map.of("musicUrl", musicUrl, "videoUrl", videoUrl);
    }

    @GetMapping("/test")
    public Map<String, String> testPresignedUrl() {
        String url = mediaUrlService.testPresignedUrl();
        return Map.of("url", url);
    }

    // 로컬 테스트: 비디오 파트1
    @GetMapping("/test/video1")
    public Map<String, String> testVideoPart1PresignedUrl() {
        String url = mediaUrlService.testPresignedUrl("video/part1.mp4");
        return Map.of("url", url);
    }

    // 로컬 테스트: 비디오 파트2
    @GetMapping("/test/video2")
    public Map<String, String> testVideoPart2PresignedUrl() {
        String url = mediaUrlService.testPresignedUrl("video/part2.mp4");
        return Map.of("url", url);
    }
}
