package main;

import model.AirbnbCrawler;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MainBNB {
    public static void main(String[] args) throws InterruptedException, IOException {
        AirbnbCrawler crawler = new AirbnbCrawler();
        crawler.crawl();

    }

}