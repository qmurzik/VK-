package com.vkmod;

import static org.junit.Assert.*;

import com.vkmod.feat.MediaLog;

import java.util.List;

import org.junit.Test;

public class MediaExtractTest {
    @Test public void photoPicksLargestSize() {
        String json = "{\"response\":{\"items\":[{\"attachments\":[{\"type\":\"photo\",\"photo\":{\"id\":7,\"owner_id\":-5,\"sizes\":["
                + "{\"url\":\"https://x/s.jpg\",\"width\":100,\"height\":100},{\"url\":\"https://x/l.jpg\",\"width\":2048,\"height\":1536},"
                + "{\"url\":\"https://x/m.jpg\",\"width\":640,\"height\":480}]}}]}]}}";
        List<MediaLog.Item> l = MediaLog.extract("wall.get", json);
        assertEquals(1, l.size());
        assertEquals("photo", l.get(0).kind);
        assertEquals("https://x/l.jpg", l.get(0).url);
        assertEquals("-5_7", l.get(0).ref);
    }

    @Test public void videoPicksBestMp4AndIgnoresHls() {
        String json = "{\"response\":{\"items\":[{\"id\":3,\"owner_id\":9,\"files\":{\"mp4_240\":\"https://v/240.mp4\",\"mp4_1080\":\"https://v/1080.mp4\",\"mp4_720\":\"https://v/720.mp4\",\"hls\":\"https://v/a.m3u8\"}}]}}";
        List<MediaLog.Item> l = MediaLog.extract("video.get", json);
        assertEquals(1, l.size());
        assertEquals("video", l.get(0).kind);
        assertEquals("https://v/1080.mp4", l.get(0).url);
    }

    @Test public void clipsAndStoriesAreCategorised() {
        String v = "{\"response\":{\"items\":[{\"id\":1,\"owner_id\":2,\"files\":{\"mp4_720\":\"https://v/c.mp4\"}}]}}";
        assertEquals("clip", MediaLog.extract("shortvideo.getOwnerVideos", v).get(0).kind);
        assertEquals("story_video", MediaLog.extract("stories.get", v).get(0).kind);
        String p = "{\"response\":{\"items\":[{\"sizes\":[{\"url\":\"https://p/1.jpg\",\"width\":10,\"height\":10}]}]}}";
        assertEquals("story_photo", MediaLog.extract("stories.get", p).get(0).kind);
    }

    @Test public void voiceMessageLink() {
        String json = "{\"response\":{\"items\":[{\"audio_message\":{\"id\":4,\"owner_id\":1,\"link_ogg\":\"https://a/v.ogg\",\"link_mp3\":\"https://a/v.mp3\"}}]}}";
        List<MediaLog.Item> l = MediaLog.extract("messages.getHistory", json);
        assertEquals(1, l.size());
        assertEquals("voice", l.get(0).kind);
        assertEquals("https://a/v.mp3", l.get(0).url);
    }

    @Test public void nothingFoundInPlainResponse() {
        assertTrue(MediaLog.extract("users.get", "{\"response\":[{\"id\":1,\"first_name\":\"A\"}]}").isEmpty());
    }
}
