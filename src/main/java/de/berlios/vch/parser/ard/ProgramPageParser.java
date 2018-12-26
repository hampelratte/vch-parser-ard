package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.BASE_URI;
import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;
import static de.berlios.vch.parser.ard.ARDMediathekParser.ID;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.VideoPage;

public class ProgramPageParser {
    private static transient Logger logger = LoggerFactory.getLogger(ProgramPageParser.class);

    public IWebPage parse(IOverviewPage opage, ResourceBundle resourceBundle) throws Exception {
        opage.getPages().clear();

        String content = HttpUtils.get(opage.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, CHARSET);
        Elements links = HtmlParserUtils.getTags(content, "div[class~=teaser] a[title]");
        for (Element link : links) {
            // create a new VideoPage
            IVideoPage video = new VideoPage();
            video.setParser(ID);

            // extract the html for each item
            String itemContent = link.html();

            // parse the video page uri
            video.setUri(new URI(BASE_URI + link.attr("href")));

            // parse the title
            video.setTitle(link.attr("title"));

            parseThumbnail(video, itemContent);
            parsePublishDate(video, itemContent);

            opage.getPages().add(video);
        }

        return opage;
    }

    private void parseThumbnail(IVideoPage video, String itemContent) {
        try {
            Element img = HtmlParserUtils.getTag(itemContent, "img");
            String src = img.attr("src");
            logger.debug("Thumbnail {}", src);
            video.setThumbnail(new URI(src));
        } catch (Throwable t) {
            logger.warn("Couldn't parse thumbnail image", t);
        }
    }

    private static void parsePublishDate(IVideoPage video, String itemContent) {
        try {
            String text = HtmlParserUtils.getText(itemContent, "h4.subline");
            Matcher dateMatcher = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4})").matcher(text);
            if (dateMatcher.find()) {
                String date = dateMatcher.group(1);
                Date pubDate = new SimpleDateFormat("dd.MM.yyyy").parse(date);
                Calendar cal = Calendar.getInstance();
                cal.setTime(pubDate);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                video.setPublishDate(cal);

                try {
                    Matcher timeMatcher = Pattern.compile("(\\d{2})\\:(\\d{2})\\s*Uhr").matcher(text);
                    if (timeMatcher.find()) {
                        int hour = Integer.parseInt(timeMatcher.group(1));
                        int minute = Integer.parseInt(timeMatcher.group(2));
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, minute);
                    } else {
                        throw new RuntimeException("Time not found in " + text);
                    }
                } catch (Throwable t) {
                    logger.debug("Publish time not found for " + video.getTitle());
                }
            } else {
                throw new RuntimeException("No publish date found in " + text);
            }
        } catch (Throwable t) {
            logger.warn("Couldn't parse publish date", t);
        }
    }
}