package main;

import java.util.Scanner;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

public class Main {
    Scanner sc = new Scanner(System.in);



    public static void main(String[] args) {
        String seedUrl = "https://www.booking.cz";//target webpage

        Document doc = retrieveHTML(seedUrl);

        if (doc != null) {
            System.out.println("HTML successfully retrieved!");
        }
    }

    private static Document retrieveHTML(String url) {
        try {
            // download HTML document using JSoup's connect class
            Document doc = Jsoup.connect(url).get();

            // log the HTML content
            System.out.println(doc.html());

            // return the HTML document
            return doc;
        } catch (IOException e) {
            // handle exceptions
            System.err.println("Unable to fetch HTML of: " + url);
        }
        return null;
    }
}
