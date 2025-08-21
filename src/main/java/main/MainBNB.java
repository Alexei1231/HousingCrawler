package main;

import files.JsonToExcel;
import model.AirbnbCrawler;
import model.AirbnbHostCrawler;
import model.AirbnbPriceCrawler;

import java.io.*;
import java.util.*;


/// TODO: parametrizace ukladani udaju, crawler hledajici cely trh
public class MainBNB {
    public static void main(String[] args) throws InterruptedException, IOException {
        Scanner scanner = new Scanner(System.in);
        int choise;
        while (true) {
            try {
                System.out.println("Prosim vyberte crawler, ktery chcete pouzit.\nVolba 1: AirBNBCrawler" +
                        " zbirajici vsechny udaje dostupne ted.\nVolba 2: AirBNBCrawler zbirajici udaje ze stranky hostitele + cenova " +
                        "mapa.\nVolba 3 anebo jine cislo: AirBNBCrawler zbirajici udaje z url-nabidky.");
                choise = scanner.nextInt();
                break;
            } catch (Exception e) {
                System.out.println("Zkuste prosime jeste jednou. Zadali jste spatne cislo.");
            }
        }
        scanner.nextLine();
        JsonToExcel jsonToExcel = new JsonToExcel();
        String url;
        int numberOfThreads, waitingPeriod, savingMode, numberOfDays;
        switch (choise) {
            case 1:
                AirbnbCrawler crawlerBnb = new AirbnbCrawler();
                crawlerBnb.crawl();
                jsonToExcel.xlsxConverter("airbnb_results.json");
            case 2:
                System.out.println("Prosim zadejte odkaz na stranku hostitele, kterou chcete skenovat");
                url = scanner.nextLine();
                numberOfThreads = 6;
                waitingPeriod = 1500;
                savingMode = 1;
                numberOfDays = 365;
                try {
                    System.out.println("Napiste prosim pocet vlaken. Idealni cislo je 6.");
                    numberOfThreads = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Byl uveden spatny pocet vlaken.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim cekaci lhutu v ms pro ceny. Idealni cislo je 1500.");
                    waitingPeriod = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim pocet dnu pro crawling.");
                    numberOfDays = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim zpusob ulozeni. 1 = obycejny(name-url-prices), jine cislo = extended(name-url-dscription-amenities-prises)");
                    savingMode = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    AirbnbHostCrawler crawlerHost = new AirbnbHostCrawler(url, numberOfDays, numberOfThreads, waitingPeriod);
                    crawlerHost.crawl();
                    if (savingMode == 1) {
                        jsonToExcel.jsonToExcelForPrices("airbnb_results.json");
                    } else {
                        jsonToExcel.jsonToExcelForPricesExtended("airbnb_results.json");
                    }
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            default:
                System.out.println("Prosim zadejte odkaz na stranku, kterou chcete skenovat");
                String url1 = scanner.nextLine();
                numberOfThreads = 6;
                waitingPeriod = 1500;
                savingMode = 1;
                numberOfDays = 365;
                try {
                    System.out.println("Napiste prosim pocet vlaken. Idealni cislo je 6.");
                    numberOfThreads = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Byl uveden spatny pocet vlaken.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim pocet dnu pro crawling.");
                    numberOfDays = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim cekaci lhutu v ms pro ceny. Idealni cislo je 1500.");
                    waitingPeriod = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    System.out.println("Napiste prosim zpusob ulozeni. 1 = obycejny(name-url-prices), jine cislo = extended(name-url-dscription-amenities-prises)");
                    savingMode = scanner.nextInt();
                } catch (Exception e) {
                    System.out.println("Bylo uvedeno spatne cislo.");
                    System.exit(0);
                }
                try {
                    AirbnbPriceCrawler crawlerPrice = new AirbnbPriceCrawler(numberOfThreads, numberOfDays, waitingPeriod);
                    crawlerPrice.crawl(url1);
                    if (savingMode == 1) {
                        jsonToExcel.jsonToExcelForPrices("airbnb_results.json");
                    } else {
                        jsonToExcel.jsonToExcelForPricesExtended("airbnb_results.json");
                    }
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }


        }

    }
}