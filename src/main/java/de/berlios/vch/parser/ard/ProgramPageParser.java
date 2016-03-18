package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.BASE_URI;
import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;
import static de.berlios.vch.parser.ard.ARDMediathekParser.ID;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.IVideoPage;
import de.berlios.vch.parser.IWebPage;
import de.berlios.vch.parser.OverviewPage;
import de.berlios.vch.parser.VideoPage;

public class ProgramPageParser {
    private static transient Logger logger = LoggerFactory.getLogger(ProgramPageParser.class);

    public IWebPage parse(IOverviewPage opage, ResourceBundle resourceBundle) throws Exception {
        opage.getPages().clear();

        String content = HttpUtils.get(opage.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, CHARSET);
        Elements tags = HtmlParserUtils.getTags(content, "div[class~=onlyWithJs] div[class~=flash] div.teaser");
        for (Element item : tags) {
            // create a new VideoPage
            IVideoPage video = new VideoPage();
            video.setParser(ID);

            // extract the html for each item
            String itemContent = item.html();

            // parse the video page uri
            Element link = HtmlParserUtils.getTag(itemContent, "a.mediaLink");
            video.setUri(new URI(BASE_URI + link.attr("href")));

            // parse the title
            Element title = HtmlParserUtils.getTag(itemContent, "h4.headline");
            video.setTitle(title.text());

            parseDescription(video, itemContent);
            parseThumbnail(video, itemContent);
            parsePublishDate(video, itemContent);

            opage.getPages().add(video);
        }

        try {
            HtmlParserUtils.getTag(content, "div[class~=paging]");
            Map<String, List<String>> params = HttpUtils.parseQuery(opage.getUri().getQuery());
            String mcontents = params.get("mcontents").get(0);
            Matcher m = Pattern.compile("(page\\.(\\d+))").matcher(mcontents);
            if (m.matches()) {
                int page = Integer.parseInt(m.group(2));
                String uri = opage.getUri().toString().replace(m.group(1), "page." + ++page);

                OverviewPage programmPage = new OverviewPage();
                programmPage.setParser(ID);
                programmPage.setTitle(resourceBundle.getString("I18N_MORE_ENTRIES"));
                programmPage.setUri(new URI(uri));
                opage.getPages().add(programmPage);
            }
        } catch (RuntimeException e) {
            // no pagination found
            logger.debug("No pagination found on page {}", opage.getUri());
        }
        return opage;
    }

    private void parseDescription(IVideoPage video, String itemContent) {
        try {
            Element desc = HtmlParserUtils.getTag(itemContent, "p.teasertext");
            video.setDescription(desc.text());
        } catch (Throwable t) {
            logger.warn("Couldn't parse description: {}", t.getLocalizedMessage());
        }
    }

    private void parseThumbnail(IVideoPage video, String itemContent) {
        try {
            Element img = HtmlParserUtils.getTag(itemContent, "a.mediaLink img");
            String json = img.attr("data-ctrl-image");
            JSONObject imgObject = new JSONObject(json);
            String src = imgObject.getString("urlScheme");
            src = src.replaceAll("##width##", "320");
            video.setThumbnail(new URI(BASE_URI + src));
        } catch (Throwable t) {
            logger.warn("Couldn't parse thumbnail image", t);
        }
    }

    private static void parsePublishDate(IVideoPage video, String itemContent) {
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