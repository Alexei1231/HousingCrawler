package main;

import files.JsonToExcel;
import model.AirbnbCrawler;
import model.AirbnbPriceCrawler;

import java.io.*;
import java.util.*;

public class MainBNB {
    public static void main(String[] args) throws InterruptedException, IOException {
        AirbnbPriceCrawler crawler = new AirbnbPriceCrawler();
        crawler.crawl(1);
        JsonToExcel jsonToExcel = new JsonToExcel();
        jsonToExcel.xlsxConverter("airbnb_results.json");


    }

}