package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
import de.berlios.vch.parser.VideoPage;
import de.berlios.vch.parser.exceptions.NoSupportedVideoFoundException;

public class VideoItemPageParser {

    private static transient Logger logger = LoggerFactory.getLogger(VideoItemPageParser.class);

    public static VideoPage parse(VideoPage page, BundleContext ctx) throws Exception {
        String content = HttpUtils.get(page.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, ARDMediathekParser.CHARSET);

        // create list of supported network protocols
        List<String> supportedProtocols = new ArrayList<String>();
        ServiceTracker st = new ServiceTracker(ctx, INetworkProtocol.class.getName(), null);
        st.open();
        Object[] protocols = st.getServices();
        for (Object object : protocols) {
            INetworkProtocol protocol = (INetworkProtocol) object;
            supportedProtocols.addAll(protocol.getSchemes());
        }
        st.close();

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
            // support for asx
            // if (bestVideo.getUri().startsWith("http")) {
            // Map<String, List<String>> headers = HttpUtils.head(bestVideo.getUri(), ARDMediathekParser.HTTP_HEADERS, ARDMediathekParser.CHARSET);
            // String contentType = HttpUtils.getHeaderField(headers, "Content-Type");
            // if ("video/x-ms-asf".equals(contentType)) {
            // bestVideo.uriPart2 = AsxParser.getUri(bestVideo.getUri());
            // }
            // }
            page.setVideoUri(new URI(bestVideo.getUri()));
            page.getUserData().put("streamName", bestVideo.uriPart2);

            logger.info("Best video found is: " + page.getVideoUri().toString());

            // parse title
            String teaserContent = HtmlParserUtils.getTag(content, "div[class~=onlyWithJs] div[class~=box] div.teaser div.textWrapper").html();
            page.setTitle(parseTitle(teaserContent));

            // parse description
            String description = parseDescription(teaserContent);
            logger.trace("Description {}", description);
            page.setDescription(description);
            page.getUserData().remove("desc");

            // parse pubDate
            try {
                Calendar date = parseDate(teaserContent);
                logger.trace("Parsed date {}", date);
                page.setPublishDate(date);
            } catch (ParseException e) {
                logger.warn("Couldn't parse publish date. Using current time!", e);
                logger.trace("Content: {}", teaserContent);
                page.setPublishDate(Calendar.getInstance());
            }

            // parse duration
            try {
                page.setDuration(parseDuration(teaserContent));
            } catch (Exception e) {
                logger.warn("Couldn't parse duration", e);
                logger.trace("Content: {}", teaserContent);
            }

            return page;
        } else {
            throw new NoSupportedVideoFoundException(page.getUri().toString(), supportedProtocols);
        }
    }

    private static long parseDuration(String content) {
        String subtitle = HtmlParserUtils.getText(content, "p.subtitle");
        String[] tokens = subtitle.split("\\s*\\|\\s*");
        String durationString = tokens[1];
        Pattern p = Pattern.compile("\\s*(\\d+):(\\d+)\\s*min");
        Matcher m = p.matcher(durationString);
        if (m.matches()) {
            int minutes = Integer.parseInt(m.group(1));
            int seconds = Integer.parseInt(m.group(2));
            return minutes * 60 + seconds;
        } else {
            return 0;
        }
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

    private static Calendar parseDate(String content) throws ParseException {
        String subtitle = HtmlParserUtils.getText(content, "p.subtitle");
        String[] tokens = subtitle.split("\\s*\\|\\s*");
        String dateString = tokens[0];
        Calendar pubDate = Calendar.getInstance();
        pubDate.setTime(new SimpleDateFormat("dd.MM.yyyy").parse(dateString));
        return pubDate;
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
