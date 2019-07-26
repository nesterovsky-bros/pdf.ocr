package nesterovskyBros.pdf.ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.leptonica.global.lept;
import org.bytedeco.tesseract.TessBaseAPI;

public class App
{
  public static void main(String[] args)
    throws Exception
  {
    String base = "C:\\projects\\git\\pdf-ocr\\bnhp.pdf.ocr\\";
    //String file = "1308103a taarifon_pr.pdf";
    String file = "doch-1.pdf";
    
    System.out.println("Start conversion");
    
    try(TessBaseAPI api = new TessBaseAPI())
    {
      System.out.println("Tessarect API is created.");

      // Initialize tesseract-ocr with English, without specifying tessdata path
      // different train data can be found at:
      // https://github.com/tesseract-ocr/tesseract/wiki/Data-Files
      if (api.Init(base + "traindata\\", "heb") != 0) {
          System.err.println("Could not initialize tesseract.");
          System.exit(1);
      }

      System.out.println("Tessarect API is initialized.");
      
      for(int i = 0; i < 1; ++i)
      {
        System.out.println("Start iteration " + i);
        System.out.println("Before open pdf.");

        try(PDDocument document = 
          PDDocument.load(new File(base + "data\\" + file)))
        {
          System.out.println("PDF is opened.");

          PDFRenderer pdfRenderer = new PDFRenderer(document);
          
          for(int page = 0; page < document.getNumberOfPages(); ++page)
          {
            System.out.println("Draw page: " + (page + 1));

            BufferedImage bim = pdfRenderer.renderImage(
              page, 
              4, 
              ImageType.BINARY, 
              RenderDestination.PRINT);
            
            String fileName = base + "out\\image-" + (i + page + 1) + ".gif";
            
            System.out.println("Save image of the page: " + (i + page + 1));

            ImageIOUtil.writeImage(bim, fileName, 100);
            
            System.out.println("Start OCR of the page: " + (i + page + 1));
            
            PIX image = lept.pixRead(fileName);
            
            try
            {
              api.SetImage(image);
              
              // Get OCR result
              String text;
              
              System.out.println(
                "Write OCR text of the page: " + (i + page + 1));

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
                Paths.get(base, "out\\text-" + (i + page + 1) + ".html");
              Files.write(textFile, lines, StandardCharsets.UTF_8);
            }
            finally
            {
              lept.pixDestroy(image);
            }
          }
        }
        
        api.Clear();

        System.out.println("Iteration " + i + " is complete.");
      }

      api.End();
    }
    
    System.out.println("Conversion complete.");
  }
}


