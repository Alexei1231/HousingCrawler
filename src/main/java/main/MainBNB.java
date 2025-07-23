package main;

import files.JsonToExcel;
import model.AirbnbPriceCrawler;

import java.io.*;
import java.util.*;

public class MainBNB {
    public static void main(String[] args) throws InterruptedException, IOException {
        AirbnbPriceCrawler crawler = new AirbnbPriceCrawler();
        crawler.crawl();
        JsonToExcel jsonToExcel = new JsonToExcel();
        jsonToExcel.xlsxConverter("airbnb_results.json");


    }

}