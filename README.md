# Scalable Web Crawler

A high-performance, distributed web crawler built with Java and Spring Boot. This system is designed to crawl thousands of URLs efficiently using a multi-threaded approach with smart politeness (delay between requests) and robust error handling.

## Features

* **Concurrent Crawling**: Uses `ExecutorService` to handle multiple URLs simultaneously.
* **Politeness**: Respects `robots.txt` and introduces a `crawl-delay` between requests to avoid overwhelming servers.
* **Scalable Architecture**: Designed to be horizontally scalable with a shared crawl queue (in a real implementation, this would be a distributed queue like Kafka/RabbitMQ).
* **Efficient Parsing**: Uses `Jsoup` for fast and reliable HTML parsing.
* **Progress Tracking**: Real-time metrics on pages crawled, currently crawling, and failed pages.
* **Deduplication**: Keeps track of visited URLs using a `ConcurrentHashMap` to avoid redundant crawling.

## Getting Started

### Prerequisites

* Java 17 or higher
* Maven 3.6+
* Docker & Docker Compose (for running Redis)

### Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd web-crawler
   ```

2. Start the Redis cache using Docker Compose:
   ```bash
   docker compose up -d
   ```

## Usage

### 1. Build the Project

Compile the code using Maven:

```bash
mvn clean package
```

### 2. Run the Crawler

Execute the main application:

```bash
mvn spring-boot:run
```

Alternatively, you can run the generated JAR:

```bash
java -jar target/web-crawler.jar
```

### 3. Configure & Start Crawling

The crawler starts automatically with default settings, but you can configure it via `application.properties`:

```properties
app.crawler.start-url=https://www.geeksforgeeks.org
app.crawler.max-depth=2
app.crawler.worker-count=10
app.crawler.crawl-delay=1000 # Milliseconds (1 second)
```

To start crawling, simply start the application. The first URL in the queue will be processed immediately.

## API Reference

### Health Check

Check if the crawler is running:

```bash
curl http://localhost:8080/actuator/health
```

### Statistics

Get current crawling statistics:

```bash
curl http://localhost:8080/api/v1/stats
```

**Response Example:**

```json
{
  "totalCrawled": 45,
  "crawling": 3,
  "queued": 12,
  "failed": 2,
  "lastCrawledUrl": "https://www.geeksforgeeks.org/queue/"
}
```

## Architecture

The system follows a producer-consumer pattern:

1. **`CrawlerStarter`**: Initializes the system and adds the starting URLs to the queue.
2. **`CrawlerQueueManager`**: Manages the shared queue (in-memory for this demo).
3. **`CrawlerWorker` (The Consumer)**:
   - Fetches the next URL from the queue.
   - Checks the `robots.txt` for the domain.
   - Waits for the `crawl-delay`.
   - Downloads the page content.
   - Parses HTML and extracts links.
   - Adds new, valid links back to the queue.

## Customization & Extension

### Changing Start URLs

Update `app.crawler.start-url` in `application.properties` to point to a different website.

### Adjusting Crawl Speed

* **Increase `app.crawler.worker-count`**: To crawl more pages in parallel (be careful not to overwhelm your network or the target server).
* **Decrease `app.crawler.crawl-delay`**: To reduce the pause between requests (use with caution).

1. Seed URL Controller : http://localhost:8080/api/v1/seed

Post request to add a list of seed URL to the queue

curl -X POST -H "Content-Type: application/json" \
  -d "[\"https://www.geeksforgeeks.org\"]" \
  http://localhost:8080/api/v1/seed


2. Get Status of the crawler 

curl http://localhost:8080/api/v1/stats

3. Get Crawled URLs

curl http://localhost:8080/api/v1/crawled

4. Get Queued URLs

curl http://localhost:8080/api/v1/queue

5. Get Failed URLs

curl http://localhost:8080/api/v1/failed

6. Health Check

curl http://localhost:8080/api/v1/health

URL Frontier:

- Use RabbitMQ with plugins to use it as a distributed queue for the URLs to be crawled.
- Deploy RabbitMQ as a docker container and use it as a message broker for the URLs to be crawled.
- Ensure politeness (crawl delay) for each domain. Use separate queues for each domain. Maintain a mapping of website hostname to download (worker) threads.
- Queue router each queue only contains URLs from the same host.
- Maintain a mapping table in redis cache mapping each host to a queue.
- Worker threads 1 to N download web pages one by one from the same host. A delay can be added between requests to the same host.
- Use postgresql to store all the crawled data in a structured format.

Storage: 

- Use postgresql to store all the crawled data in a structured format.
- Keep track of visited URLs in redis cache for fast lookup.

HTML Downloader:

- Use Apache HttpClient to download web pages.
- Use DNS resolver to get corresponding IP address for the URL.
- Cache DNS entries to avoid repeated DNS lookups.
- Check for content type. Only crawl HTML pages.
- Connection pool to limit the number of connections to the same host.

Content Parser:
- Use JSoup to parse HTML pages and extract links.
- Checks the `robots.txt` for the domain. Follow rules of robots.txt to download the web pages.
- Use a data structure to identify if content is already seen.
- Store content in Postgresql table 
 
URL Filter:
- URL Filter is used to filter out the URLs that are not to be crawled.
- Exclude certain content types, file extensions, error links and URLs in blacklisted sites

URL seen:
- Keep track of URL already visited or already in URL frontier. Avoid adding same URL multiple times to the URL frontier.
- Avoid potential infinite loops by checking URL depth and duplicate URLs.
- Use postgres for storing visited URLs in a structured format.




