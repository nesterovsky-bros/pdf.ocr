package nesterovskyBros.pdf.ocr;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.Kernel;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.GrayFilter;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.global.lept;
import org.bytedeco.tesseract.TessBaseAPI;

public class App2
{
  public static void main(String[] args)
    throws Exception
  {
    String[] images = 
    { 
      "C:\\temp\\images\\cheque1.jpg", 
      //"C:\\temp\\images\\cheque2.jpg" 
    };
    
    for(String path: images)
    {
      File file = new File(path);
      String name = file.getName();
      File folder = file.getParentFile();
      BufferedImage image = ImageIO.read(file);
      
      BufferedImage grayImage = new BufferedImage(
        image.getWidth(), 
        image.getHeight(), 
        BufferedImage.TYPE_INT_ARGB);

      ColorConvertOp op = 
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

      op.filter(image, grayImage);
      
      ImageIO.write(
        grayImage, 
        "png",
        new File(
          folder, 
          "gray-" + name.substring(0, name.lastIndexOf('.')) + ".png"));
      
      int radius = 10;
      int size = radius * 2 + 1;
      float weight = 1.0f / (size * size);
      float[] data = new float[size * size];

      for(int i = 0; i < data.length; i++)
      {
          data[i] = weight;
      }
      
      Kernel kernel = new Kernel(size, size, data);
      ConvolveOp operation = 
        new ConvolveOp(kernel, ConvolveOp.EDGE_ZERO_FILL, null);

      BufferedImage bluredImage = operation.filter(grayImage, null);      

      ImageIO.write(
        bluredImage, 
        "png",
        new File(
          folder, 
          "blured-" + name.substring(0, name.lastIndexOf('.')) + ".png"));
    }

    if (true)
    {
      return;
    }
    
    String base = "C:\\projects\\git\\pdf-ocr\\";
    
    try(TessBaseAPI api = new TessBaseAPI())
    {
      System.out.println("Tessarect API is created.");

      // Initialize tesseract-ocr with English, without specifying tessdata path
      // different train data can be found at:
      // https://github.com/tesseract-ocr/tesseract/wiki/Data-Files
      if (api.Init(base + "traindata\\", "eng+heb") != 0) {
          System.err.println("Could not initialize tesseract.");
          System.exit(1);
      }

      System.out.println("Tessarect API is initialized.");
      
      for(int page = 0; page < images.length; ++page)
      {
        if (page != 4)
        {
          continue;
        }
        String fileName = images[page];
          
        System.out.println("Start OCR of the page: " + (page + 1));
          
        PIX image = lept.pixRead(fileName);
        
        try
        {
          api.SetImage(image);
          
          // Get OCR result
          String text;
          
          System.out.println(
            "Write OCR text of the page: " + (page + 1));

          try(BytePointer outText = api.GetHOCRText(page))
          {
            text = outText.getString();
          }
          
          List<String> lines = Arrays.asList(
            "<html><head><meta charset=\"utf-8\"/>" +
            "<style>  .ocr_line { display: block; }</style></head><body>",
            text,
            "</body></html>");
          
          Path textFile = 
            Paths.get(base, "out\\text-" + (page + 1) + ".html");
          Files.write(textFile, lines, StandardCharsets.UTF_8);
        }
        finally
        {
          lept.pixDestroy(image);
        }
      }
      
//      api.Clear();

      api.End();
    }
  }
}