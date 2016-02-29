package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.net.INetworkProtocol;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.VideoPage;
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
            URI uri = new URI(video.getUri());
            if (supportedProtocols.contains(uri.getScheme())) {
                bestVideo = video;
                break;
            }
        }

        if (bestVideo != null) {
            page.setVideoUri(new URI(bestVideo.getUri()));
            page.getUserData().put("streamName", bestVideo.uriPart2);

            logger.info("Best video found is: " + page.getVideoUri().toString());

            // parse title
            String teaserContent = HtmlParserUtils.getTag(content, "div[class~=modClipinfo] div.teaser div.textWrapper").html();
            page.setTitle(parseTitle(teaserContent));

            // parse description
            String description = parseDescription(teaserContent);
            logger.trace("Description {}", description);
            page.setDescription(description);
            page.getUserData().remove("desc");

            parsePublishDate(page, teaserContent);
            parseDuration(page, teaserContent);

            return page;
        } else {
            throw new NoSupportedVideoFoundException(page.getUri().toString(), supportedProtocols);
        }
    }

    private static void parseDuration(IVideoPage video, String itemContent) {
        String text = HtmlParserUtils.getText(itemContent, "p.subtitle");
        Matcher m = Pattern.compile("\\|\\s*(\\d+)\\s*Min\\.\\s*\\|").matcher(text);
        if (m.find()) {
            int minutes = Integer.parseInt(m.group(1));
            video.setDuration(minutes * 60);
        } else {
            logger.debug("No duration information found: {}", text);
        }
    }

    private static void parsePublishDate(IVideoPage video, String itemContent) {
        if (video.getPublishDate() != null) {
            return;
        }

        try {
            String text = HtmlParserUtils.getText(itemContent, "p.subtitle");
            text += " " + HtmlParserUtils.getText(itemContent, "p.dachzeile");
            Matcher dateMatcher = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})").matcher(text);
            if (dateMatcher.find()) {
                String date = dateMatcher.group(1);
                Date pubDate = new SimpleDateFormat("dd.MM.yyyy").parse(date);
                Calendar cal = Calendar.getInstance();
                cal.setTime(pubDate);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                video.setPublishDate(cal);
            } else {
                throw new RuntimeException("No publish date found in " + text);
            }
        } catch (Throwable t) {
            logger.warn("Couldn't parse publish date", t);
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
            desc = HtmlParserUtils.getText(content, "p.teasertext");
        } catch (RuntimeException e) {
            desc = "";
        }
        return desc;
    }

    private static String parseTitle(String content) {
        return HtmlParserUtils.getText(content, "h4.headline");
    }

    private static List<VideoType> parseAvailableVideos(String content) throws URISyntaxException, JSONException, IOException {
        List<VideoType> videos = new ArrayList<VideoType>();

        Element div = HtmlParserUtils.getTag(content, "div[class~=modPlayer] div[class~=media\\s]");
        String playerParamsAttr = div.attr("data-ctrl-player");
        logger.info("Player params:[{}]", playerParamsAttr);
        if ("".equals(playerParamsAttr)) {
            logger.info("Skipping section without player params");
            Elements divs = HtmlParserUtils.getTags(content, "div[class~=modPlayer] div[class~=media\\s]");
            logger.info("Divs:_{}", divs.size());
            playerParamsAttr = div.attr("data-ctrl-player");
        }

        if (!playerParamsAttr.isEmpty()) {
            JSONObject playerParams = new JSONObject(playerParamsAttr);

            String streamOptionsUri = ARDMediathekParser.BASE_URI + playerParams.getString("mcUrl");
            JSONObject streamOptions = new JSONObject(HttpUtils.get(streamOptionsUri, null, CHARSET));
            logger.trace("Stream options:\n{}", streamOptions.toString());
            JSONArray mediaArray = streamOptions.getJSONArray("_mediaArray");
            for (int i = 0; i < mediaArray.length(); i++) {
                JSONObject plugin = mediaArray.getJSONObject(i);
                JSONArray mediaStreamArray = plugin.getJSONArray("_mediaStreamArray");
                for (int j = 0; j < mediaStreamArray.length(); j++) {
                    JSONObject mediaStream = mediaStreamArray.getJSONObject(j);
                    String uriPart1 = "";
                    if (mediaStream.has("_server")) {
                        uriPart1 = mediaStream.getString("_server");
                    }

                    Object _stream = mediaStream.get("_stream");
                    String uriPart2 = "";
                    if (_stream instanceof String) {
                        uriPart2 = _stream.toString();
                    } else if (_stream instanceof JSONArray) {
                        JSONArray array = (JSONArray) _stream;
                        uriPart2 = array.getString(0);
                    }

                    // for http uris
                    if (!mediaStream.has("flashUrl") || !mediaStream.getBoolean("flashUrl")) {
                        uriPart1 = uriPart2;
                        uriPart2 = "";
                    }

                    Object _quality = mediaStream.get("_quality");
                    int quality = 0;
                    if (_quality instanceof Integer) {
                        quality = ((Integer) _quality).intValue();
                    } else if ("auto".equals(_quality)) {
                        continue;
                    }

                    VideoType vt = new VideoType(uriPart1, uriPart2, VideoType.FORMAT.MP4, quality);
                    videos.add(vt);
                }

            }
        }
        return videos;
    }

    /**
     * Container class for the different video types and qualities. The URI is split into uriPart1 and uriPart2. This is needed for rtmp streams.
     */
    public static class VideoType {
        private String uriPart1;
        private String uriPart2;
        private FORMAT format;
        private int quality;

        public static enum FORMAT {
            MP4, WMV, MPG
        }

        public VideoType(String uriPart1, String uriPart2, FORMAT format, int quality) {
            super();
            this.uriPart1 = uriPart1;
            this.uriPart2 = uriPart2;
            this.format = format;
            this.quality = quality;
        }

        public String getUri() {
            String uri = "";
            if (uriPart1 != null && uriPart1.length() > 0) {
                uri += uriPart1;
                if (!(uriPart1.endsWith("/") || uriPart2.startsWith("/")) && !uriPart2.isEmpty()) {
                    uriPart1 += "/";
                }
            }
            uri += uriPart2;
            return uri;
        }

        public String getUriPart1() {
            return uriPart1;
        }

        public void setUriPart1(String uriPart1) {
            this.uriPart1 = uriPart1;
        }

        public String getUriPart2() {
            return uriPart2;
        }

        public void setUriPart2(String uriPart2) {
            this.uriPart2 = uriPart2;
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
