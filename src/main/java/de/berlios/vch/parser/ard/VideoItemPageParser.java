package de.berlios.vch.parser.ard;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.net.INetworkProtocol;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.ard.VideoItemPageParser.VideoType.FORMAT;
import de.berlios.vch.parser.exceptions.NoSupportedVideoFoundException;

public class VideoItemPageParser {

    private static transient Logger logger = LoggerFactory.getLogger(VideoItemPageParser.class);

    public static VideoPage parse(VideoPage page, BundleContext ctx) throws Exception {
        String content = HttpUtils.get(page.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, ARDMediathekParser.CHARSET);

        // create list of supported network protocols
        List<String> supportedProtocols = getSupportedProtocols(ctx);

        // first parse the available formats for this video
        List<VideoType> videos = parseAvailableVideos(content);

        // sort by best format and quality
        Collections.sort(videos, new VideoTypeComparator());

        // find the first supported protocol
        VideoType bestVideo = null;
        for (VideoType video : videos) {
            URI uri = new URI(video.getUrl());
            if (supportedProtocols.contains(uri.getScheme())) {
                bestVideo = video;
                break;
            }
        }

        if (bestVideo != null) {
            page.setVideoUri(new URI(bestVideo.getUrl()));
            logger.info("Best video found is: " + page.getVideoUri().toString());

            // parse description
            String description = parseDescription(content);
            logger.trace("Description {}", description);
            page.setDescription(description);
            return page;
        } else {
            throw new NoSupportedVideoFoundException(page.getUri().toString(), supportedProtocols);
        }
    }

    private static List<String> getSupportedProtocols(BundleContext ctx) {
        List<String> supportedProtocols = new ArrayList<String>();
        ServiceTracker<INetworkProtocol, INetworkProtocol> st = new ServiceTracker<INetworkProtocol, INetworkProtocol>(ctx, INetworkProtocol.class, null);
        st.open();
        Object[] protocols = st.getServices();
        for (Object object : protocols) {
            INetworkProtocol protocol = (INetworkProtocol) object;
            supportedProtocols.addAll(protocol.getSchemes());
        }
        st.close();
        return supportedProtocols;
    }

    private static String parseDescription(String content) {
        String desc = null;
        try {
            desc = HtmlParserUtils.getText(content, "div[class~=information] p[class~=teasertext]");
        } catch (RuntimeException e) {
            desc = "";
        }
        return desc;
    }

    private static List<VideoType> parseAvailableVideos(String content) throws URISyntaxException, JSONException, IOException {
        List<VideoType> videos = new ArrayList<VideoType>();

        Matcher m = Pattern.compile("window.__APOLLO_STATE__\\s*=\\s*(.*?);").matcher(content);
        if (m.find()) {
            JSONObject json = new JSONObject(m.group(1));
            String[] names = JSONObject.getNames(json);
            for (String name : names) {
                if (name.endsWith(".mediaCollection")) {
                    JSONObject mediaCollection = json.getJSONObject(name);
                    JSONArray mediaArray = mediaCollection.getJSONArray("_mediaArray");
                    JSONObject media = mediaArray.getJSONObject(0);
                    String id = media.getString("id");
                    JSONObject mediaArray0 = json.getJSONObject(id);
                    JSONArray mediaStreamArray = mediaArray0.getJSONArray("_mediaStreamArray");
                    for (int i = 0; i < mediaStreamArray.length(); i++) {
                        JSONObject mediaStream = mediaStreamArray.getJSONObject(i);
                        id = mediaStream.getString("id");
                        JSONObject streamConfig = json.getJSONObject(id);
                        String quali = streamConfig.getString("_quality");
                        JSONObject stream = streamConfig.getJSONObject("_stream");
                        String url = stream.getJSONArray("json").getString(0);
                        if(url.startsWith("//")) {
                            url = "https:" + url;
                        }
                        if("auto".equals(quali)) {
                            videos.add(new VideoType(url, FORMAT.HLS, quali));
                        } else {
                            videos.add(new VideoType(url, FORMAT.MP4, quali));
                        }
                    }
                }
            }
        }
        return videos;
    }

    /**
     * Container class for the different video types and qualities
     */
    public static class VideoType {
        private String url;
        private FORMAT format;
        private int quality;

        public static enum FORMAT {
            MP4, HLS
        }

        public VideoType(String url, FORMAT format, String quality) {
            super();
            this.url = url;
            this.format = format;
            if("auto".equals(quality)) {
                this.quality = -1;
            } else {
                this.quality = Integer.parseInt(quality);
            }
        }

        public String getUrl() {
            return url;
        }

        public FORMAT getFormat() {
            return format;
        }

        public void setFormat(FORMAT format) {
            this.format = format;
        }

        public int getQuality() {
            return quality;
        }

        public void setQuality(int quality) {
            this.quality = quality;
        }
    }
}
