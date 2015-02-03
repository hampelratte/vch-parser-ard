package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;

import java.net.URI;
import java.util.Iterator;

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
import de.berlios.vch.parser.VideoPage;

public class ProgramPageParser {
    private static transient Logger logger = LoggerFactory.getLogger(ProgramPageParser.class);

    public IWebPage parse(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, CHARSET);
        Elements teasers = HtmlParserUtils.getTags(content, "div[class~=onlyWithJs] div[class~=flash] div.teaser");
        for (Iterator<Element> iterator = teasers.iterator(); iterator.hasNext();) {
            IVideoPage videoPage = new VideoPage();
            videoPage.setParser(ARDMediathekParser.ID);
            opage.getPages().add(videoPage);

            Element teaser = iterator.next();
            String teaserContent = teaser.html();
            String title = HtmlParserUtils.getText(teaserContent, "h4.headline");
            String subtitle = HtmlParserUtils.getText(teaserContent, "p.subtitle");
            videoPage.setTitle(title + " - " + subtitle);

            // parse oage uri
            Element pageLink = HtmlParserUtils.getTag(teaserContent, "a.mediaLink");
            String programPageUri = ARDMediathekParser.BASE_URI + pageLink.attr("href");
            videoPage.setUri(new URI(programPageUri));

            // parse thumbnail
            try {
                Element thumb = HtmlParserUtils.getTag(teaserContent, "a.mediaLink img");
                String jsonString = thumb.attr("data-ctrl-image");
                JSONObject json = new JSONObject(jsonString);
                String path = json.getString("urlScheme").replaceAll("##width##", "256");
                String thumbUri = ARDMediathekParser.BASE_URI + path;
                videoPage.setThumbnail(new URI(thumbUri));
            } catch (Exception e) {
                logger.warn("Couldn't parse thumbnail", e);
            }
        }

        // TODO möglichkeit für weitere seiten schaffen

        return opage;
    }
}