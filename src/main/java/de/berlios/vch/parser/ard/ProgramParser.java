package de.berlios.vch.parser.ard;

import static de.berlios.vch.parser.ard.ARDMediathekParser.CHARSET;

import java.net.URI;
import java.util.Iterator;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.HtmlParserUtils;
import de.berlios.vch.parser.IOverviewPage;
import de.berlios.vch.parser.OverviewPage;

public class ProgramParser {

    public IOverviewPage parse(IOverviewPage opage) throws Exception {
        String content = HttpUtils.get(opage.getUri().toString(), ARDMediathekParser.HTTP_HEADERS, CHARSET);
        Elements teasers = HtmlParserUtils.getTags(content, "div[class~=onlyWithJs] div.box div.teaser");
        for (Iterator<Element> iterator = teasers.iterator(); iterator.hasNext();) {
            Element teaser = iterator.next();

            String teaserContent = teaser.html();

            String title = HtmlParserUtils.getText(teaserContent, "h4.headline");
            String subtitle = HtmlParserUtils.getText(teaserContent, "p.subtitle");
            Element pageLink = HtmlParserUtils.getTag(teaserContent, "a.mediaLink");
            String programPageUri = ARDMediathekParser.BASE_URI + pageLink.attr("href");

            IOverviewPage programPage = new OverviewPage();
            programPage.setParser(ARDMediathekParser.ID);
            programPage.setTitle(title + " - " + subtitle);
            programPage.setUri(new URI(programPageUri));
            opage.getPages().add(programPage);
        }
        return opage;
    }
}
