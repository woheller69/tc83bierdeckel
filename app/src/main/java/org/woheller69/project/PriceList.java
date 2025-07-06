package org.woheller69.project;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PriceList {
    private static final String FILE = "pricelist.txt";
    private static final List<String> priceList =  new ArrayList<>();
    @SuppressLint("ConstantLocale")
    private static final Locale locale = Locale.getDefault();


    public String getPriceListDate(Context context){
        File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/"+FILE);
        String date="";
        if (!file.exists()) {
            return "";
        }

        try {
            FileReader in = new FileReader(file);
            BufferedReader reader = new BufferedReader(in) ;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Date:"))  {
                    date=line.split("Date:")[1];
                    in.close();
                    break;
                }
            }
            in.close();

        } catch (IOException i) {
            Log.w("PriceList", "Error getting price list date", i);
        }
        return date;
    }

    public List<String> loadPriceList(final Context context) {
        try {
            File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/"+FILE);
            FileReader in = new FileReader(file);
            BufferedReader reader = new BufferedReader(in) ;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank())  continue;
                priceList.add(line);
            }
            in.close();
            return priceList;
        } catch (IOException i) {
            Log.w("PriceList", "Error loading price list", i);
            return null;
        }
    }

    public static void downloadPriceList(final Context context) {
        Thread thread = new Thread(() -> {

            String hostURL = "https://github.com/woheller69/tc83bierdeckel/raw/refs/heads/master/pricelist.txt";

            try {
                URL url = new URL(hostURL);
                Log.d("PriceList","Download price list");

                SpannableStringBuilder biggerText = new SpannableStringBuilder("\u27f3 Preisliste");
                biggerText.setSpan(new RelativeSizeSpan(1.35f), 0, 1, 0);
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, biggerText, Toast.LENGTH_LONG).show();
                });
                URLConnection ucon = url.openConnection();
                ucon.setReadTimeout(5000);
                ucon.setConnectTimeout(10000);

                InputStream is = ucon.getInputStream();
                BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                File tempfile = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/temp.txt");

                if (tempfile.exists())
                {
                    tempfile.delete();
                }
                tempfile.createNewFile();

                FileOutputStream outStream = new FileOutputStream(tempfile);
                byte[] buff = new byte[5 * 1024];

                int len;
                while ((len = inStream.read(buff)) != -1)
                {
                    outStream.write(buff, 0, len);
                }

                outStream.flush();
                outStream.close();
                inStream.close();

                FileReader in = new FileReader(tempfile);
                BufferedReader reader = new BufferedReader(in) ;
                File outfile = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/"+FILE);
                FileWriter out = new FileWriter(outfile);
                String line;
                while ((line = reader.readLine()) != null) {
                    out.write(line+"\n");
                }
                in.close();
                out.close();

                tempfile.delete();
                priceList.clear();
                Log.w("PriceList", "Price list updated");
                ((Activity) context).runOnUiThread(()->{((Activity) context).recreate();});

            } catch (IOException i) {
                Log.w("PriceList", "Error updating price list", i);
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, "Fehler beim Update der Preisliste", Toast.LENGTH_LONG).show();
                });
            }
        });
        thread.start();
    }

    public static List<String> getPriceList() {
        return priceList;
    }

    public PriceList(Context context, boolean update) {
        File file = new File(context.getDir("filesdir", Context.MODE_PRIVATE) + "/"+FILE);
        if (!file.exists()) {
            //copy pricelist.txt from assets if not available
            Log.d("PriceList file","does not exist");
            try {
                AssetManager manager = context.getAssets();
                copyFile(manager.open(FILE), Files.newOutputStream(file.toPath()));
                downloadPriceList(context);  //try to update pricelist.txt from internet
            } catch(IOException e) {
                Log.e("PriceList", "Failed to copy asset file", e);
            }
        } else {
            Calendar time = Calendar.getInstance();
            time.add(Calendar.DAY_OF_YEAR,-30);  //once a month

            Date lastModified = new Date(file.lastModified());
            if (lastModified.before(time.getTime())|| getPriceListDate(context).equals("")) {  //also download again if something is wrong with the file
                //update if file is older than 30 days if no open bill
                if (update) downloadPriceList(context);
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

}
