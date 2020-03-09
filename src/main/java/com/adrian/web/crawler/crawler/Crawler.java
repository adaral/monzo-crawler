package com.adrian.web.crawler.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adrian.web.crawler.model.Page;
import com.adrian.web.crawler.model.Sitemap;
import com.adrian.web.crawler.utils.CrawlerUtils;

public class Crawler implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Crawler.class);

	private final List<String> visited;

	private final BlockingQueue<Page> queue;

	private final String firstURL;

	private final Sitemap sitemap;

	private final List<String> disallowedURLs;

	private final Boolean showLog;

	/*
	 * Constructor
	 */
	public Crawler(String url, Sitemap s, List<String> visited, BlockingQueue<Page> queue, List<String> disallowedURLs,
			Boolean showLog) {
		this.firstURL = url;
		this.sitemap = s;
		this.queue = queue;
		this.visited = visited;
		this.disallowedURLs = disallowedURLs;
		this.showLog = showLog;
	}

	@Override
	public void run() {
		/*
		 * While the queue is not empty, poll the element from the head of the queue,
		 * add it to the visited list, and crawl it to get its links
		 */
		while (!queue.isEmpty()) {
			Page page = queue.poll();
			/*
			 * Check if it's been visited already. If it hasn't, crawl it
			 */
			if (!visited.contains(page.getUrl())) {
				visited.add(page.getUrl());
				crawl(page);
			}

		}
	}

	private void crawl(Page page) {

		try {
			/*
			 * Get the document and its a href elements with jsoup
			 */
			Document document = Jsoup.connect(page.getUrl()).get();
			Elements linksOnPage = document.select("a[href]");
			List<String> links = new ArrayList<>();
			/*
			 * Iterate over all elements
			 */
			for (Element link : linksOnPage) {
				String linkURL = link.attr("abs:href");
				/*
				 * If the link is empty, ignore it
				 */
				if (StringUtils.isEmpty(linkURL))
					continue;
				/*
				 * Delete all trailing characters that are not letters, such as / and #. This is
				 * done to avoid duplicates, as www.monzo.com and www.monzo.com/ would be trated
				 * as different, but will have the same links
				 */
				while (!Character.isLetter(linkURL.charAt(linkURL.length() - 1))) {
					linkURL = linkURL.substring(0, linkURL.length() - 1);
				}
				/*
				 * Check if the current URL is in the disallowed list, if they belong to the
				 * same domain, if it hasn't been visited and if it's not already in the queue
				 */
				if (disallowedURLs.stream().noneMatch(linkURL::startsWith)
						&& CrawlerUtils.isSameDomain(linkURL, firstURL)) {
					/*
					 * If it's not in the queue, create a new page and add it
					 */
					if (queue.stream().map(Page::getUrl).noneMatch(linkURL::equals)) {
						Page linkedPage = new Page();
						linkedPage.setUrl(linkURL);
						queue.add(linkedPage);
					}

					/*
					 * Add the list of links to the page
					 */
					links.add(linkURL);

				}
			}
			/*
			 * Add page to sitemap
			 */
			page.setLinks(links);
			if (showLog)
				LOG.info("Crawled {}. Found {} valid links", page.getUrl(), page.getLinks().size());
			sitemap.addPage(page);

		} catch (IOException e) {
			LOG.error("Error reading {} Message: {}", page.getUrl(), e.getMessage());
		}

	}

}
