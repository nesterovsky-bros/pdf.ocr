package nesterovskyBros.pdf.ocr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.text.SetFontAndSize;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDFontFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;
import org.apache.pdfbox.rendering.RenderDestination;
import org.apache.pdfbox.tools.PDFText2HTML;
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
    String base = "C:\\projects\\git\\pdf-ocr\\";
    //String file = "1308103a taarifon_pr.pdf";
    String file = "doch-1.pdf";
    
    System.out.println("Start conversion");
    
    System.out.println("Before open pdf.");
    String[] images;

    try(PDDocument document = 
      PDDocument.load(new File(base + "data\\" + file)))
    {
      System.out.println("PDF is opened.");
      
//      System.out.println("Replace fonts.");
      
      PDFont defaultFont = PDFontFactory.createDefaultFont();
      PDFontDescriptor defaultFontDescriptor = defaultFont.getFontDescriptor();
//  
if (true)
{  
      for(PDPage page: document.getPages()) 
      {
        PDResources resources = page.getResources();
        
        for(COSName key: resources.getFontNames()) 
        {
          PDFont font = resources.getFont(key);
          
//          System.out.println(font.getFontDescriptor().getFontName());

//          resources.put(key, defaultFont);
          
          PDFontDescriptor fontDescriptor = font.getFontDescriptor();
          
          fontDescriptor.setFontName(defaultFontDescriptor.getFontName());
          fontDescriptor.setFontFamily(defaultFontDescriptor.getFontFamily());
          
          COSDictionary cosObj = fontDescriptor.getCOSObject();
          Set<COSName> fontNameSet = new HashSet<>();
          
          for(COSName name: cosObj.keySet())
          {
            if(name.getName().startsWith("FontFile"))
            {
                fontNameSet.add(name);
            }
          }

          for(COSName name: fontNameSet)
          {
            cosObj.removeItem(name);
          }
        }
      }
}

      images = new String[document.getNumberOfPages()];

      PDFRenderer pdfRenderer = new PDFRenderer(document)
      {
        @Override
        protected PageDrawer createPageDrawer(PageDrawerParameters parameters)
          throws IOException
        {
          PageDrawer pageDrawer = super.createPageDrawer(parameters);
          
          pageDrawer.addOperator(new SetFontAndSize()
          {
            @Override
            public void process(Operator operator, List<COSBase> arguments)
              throws IOException
            {
              //PDFont font = context.getGraphicsState().getTextState().getFont();

              super.process(operator, arguments);
              //context.getGraphicsState().getTextState().setFont(font);
              
              float fontSize = 
                context.getGraphicsState().getTextState().getFontSize();
              
              //if (fontSize > 8)
              {
                context.getGraphicsState().getTextState().setFontSize(10);
              }
            }
          });
          
          return pageDrawer;
        }    
      };
      
      for(int page = 0; page < document.getNumberOfPages(); ++page)
      {
        if (page != 4)
        {
          continue;
        }

        System.out.println("Draw page: " + (page + 1));
        
        float scale = 8;

        BufferedImage bim = pdfRenderer.renderImage(
          page, 
          scale, 
          ImageType.BINARY, 
          RenderDestination.VIEW);
        
        String fileName = base + "out\\image-" + (page + 1) + ".gif";
        
        images[page] = fileName;
        
        System.out.println("Save image of the page: " + (page + 1));

        ImageIOUtil.writeImage(bim, fileName, (int)(scale * 72));
      }
    }
    
    if (false)
    {
      return;
    }
    
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

    System.out.println("Conversion complete.");
  }
}
