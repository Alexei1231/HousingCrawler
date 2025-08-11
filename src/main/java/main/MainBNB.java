package main;

import files.JsonToExcel;
import model.AirbnbCrawler;
import model.AirbnbHostCrawler;
import model.AirbnbPriceCrawler;

import java.io.*;
import java.util.*;

public class MainBNB {
    public static void main(String[] args) throws InterruptedException, IOException {
//        AirbnbCrawler crawler = new AirbnbCrawler();
//        crawler.crawl();
//        JsonToExcel jsonToExcel = new JsonToExcel();
//        jsonToExcel.xlsxConverter("airbnb_results.json");
        System.out.println("Prosim zadejte odkaz na stranku, kterou chcete skenovat");
        Scanner scanner = new Scanner(System.in);
        String url = scanner.nextLine();
        AirbnbHostCrawler crawler = new AirbnbHostCrawler(url);
        crawler.crawl();

        System.exit(0);
    }

}