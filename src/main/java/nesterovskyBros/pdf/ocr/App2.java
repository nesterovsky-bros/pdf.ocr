package nesterovskyBros.pdf.ocr;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageFilter;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
import java.awt.image.ImageProducer;
import java.awt.image.Kernel;
import java.awt.image.PixelGrabber;
import java.awt.image.RGBImageFilter;
import java.awt.image.RenderedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

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
    
    String[] maskedImages = new String[images.length];
    
    for(int index = 0; index < images.length; ++index)
    {
      String path= images[index];
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
      
      int radius = 5;
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
      
      ImageFilter maskFilter = new RGBImageFilter()
      {
        int maskSize = 3;
        int threshold = 0x90; 
        int background = 0xffffffff;
        BufferedImage maskImage = bluredImage;
        int maskWidth = maskImage.getWidth();
        int maskHeight = maskImage.getHeight();
        int[] pixels = new int[maskSize * maskSize];

        @Override
        public int filterRGB(int x, int y, int rgb)
        {
          boolean hasValue = false;
          int value = 0xff;
          
          if (maskSize < 2)
          {
            int color = maskImage.getRGB(x, y);
            
            if ((color & 0xff000000) >= 0xf0000000)
            {
              value = color & 0xff;
              hasValue = true;
            }
          }
          else
          {
            int startX = x - maskSize / 2;
            int startY = y - maskSize / 2;
            int width = maskSize;
            int height = maskSize;
            boolean reset = false;
            
            if (startX < 0)
            {
              width += startX;
              startX = 0;
              reset = true;
            }
            
            if (startX + width > maskWidth)
            {
              width += maskWidth - (startX + width);
              reset = true;
            }

            if (startY < 0)
            {
              height += startY;
              startY = 0;
              reset = true;
            }

            if (startY + height > maskHeight)
            {
              height += maskHeight - (startY + height);
              reset = true;
            }
            
            if (reset)
            {
              for(int i = 0; i < pixels.length; ++i)
              {
                pixels[i] = 0xff;
              }
            }
            
            maskImage.getRGB(
              startX,
              startY,
              width,
              height, 
              pixels, 
              0, 
              maskSize);
            
            for(int color: pixels)
            {
              if ((color & 0xff000000) >= 0xf0000000)
              {
                hasValue = true;
                
                if (value > (color & 0xff))
                {
                  value = color & 0xff;
                }
              }
            }
          }
          
          return hasValue && (value <= threshold) && 
            ((rgb & 0xff) <= threshold) ? 
            rgb : background;
        }
      };

      PixelGrabber grabber = new PixelGrabber(
        new FilteredImageSource(grayImage.getSource(), maskFilter), 
        0, 
        0, 
        -1, 
        -1, 
        null, 
        0,
        0);
      
      grabber.grabPixels();

      BufferedImage maskedImage = 
        new BufferedImage(
          grabber.getWidth(), 
          grabber.getHeight(),
          BufferedImage.TYPE_INT_ARGB);
      
      maskedImage.setRGB(
        0, 
        0, 
        grabber.getWidth(), 
        grabber.getHeight(),
        (int[])grabber.getPixels(), 
        0, 
        grabber.getWidth());
      
      File maskedFile = new File(
        folder, 
        "masked-" + name.substring(0, name.lastIndexOf('.')) + ".png"); 
      
      //ImageIO.write(maskedImage, "png", maskedFile);
      saveImage(maskedImage, "png", maskedFile, 96);
      
      maskedImages[index] = maskedFile.getAbsolutePath();
    }

//    if (true)
//    {
//      return;
//    }
    
    maskedImages = new String[]
    {
      "C:\\temp\\images\\cheque1.jpg",
      "C:\\temp\\images\\gray-cheque1.png",
      "C:\\temp\\images\\blured-cheque1.png",
      "C:\\temp\\images\\masked-cheque1.png"
    };
    
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
      
      for(int page = 0; page < maskedImages.length; ++page)
      {
//        if (page != 4)
//        {
//          continue;
//        }
        
        String fileName = maskedImages[page];
        File file = new File(fileName);
        String name = file.getName();
        File folder = file.getParentFile();
          
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
          
//          Path textFile = 
//            Paths.get(base, "out\\text-" + (page + 1) + ".html");
          
          Path textFile = Paths.get(
            folder.getAbsolutePath(), 
            "ocr-" + name.substring(0, name.lastIndexOf('.')) + ".html");
          
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
  
  /**
   * Save the buffered image to disk.
   *
   * If a PNG is passed add the dot per meter to the pHYS chunkc of the PNG metadata
   * @See PNG Metadata Format Specification - https://docs.oracle.com/javase/8/docs/api/javax/imageio/metadata/doc-files/png_metadata.html
   *
   * <!-- The pHYS chunk, containing the pixel size and aspect ratio -->
   * <!ATTLIST "pHYS" "pixelsPerUnitXAxis" #CDATA #REQUIRED>
   * <!-- The number of horizontal pixels per unit, multiplied by 1e5 -->
   * <!ATTLIST "pHYS" "pixelsPerUnitYAxis" #CDATA #REQUIRED>
   * <!-- The number of vertical pixels per unit, multiplied by 1e5 -->
   * <!ATTLIST "pHYS" "unitSpecifier" ("unknown" | "meter") #REQUIRED>
   * <!-- The unit specifier for this chunk (i.e., meters) -->
   *
   *
   * @param bufferedImage image
   * @param formatName PNG, TIFF, etc..
   * @param localOutputFile local filename whene to save te image
   * @param dpi               dot per inches of the image to save
   * @return true if successful, false otherwise
   * @throws IOException
   */
  static boolean saveImage(RenderedImage bufferedImage,
                    String formatName,
                    File localOutputFile,
                    int dpi) throws Exception {
      boolean success;

      if (formatName.equalsIgnoreCase("png"))
      {
          ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();

          ImageWriteParam writeParam = writer.getDefaultWriteParam();
          ImageTypeSpecifier typeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);

          IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, writeParam);

          final String pngMetadataFormatName = "javax_imageio_png_1.0";

          // Convert dpi (dots per inch) to dots per meter
          final double metersToInches = 39.3701;
          int dotsPerMeter = (int) Math.round(dpi * metersToInches);

          IIOMetadataNode pHYs_node = new IIOMetadataNode("pHYs");
          pHYs_node.setAttribute("pixelsPerUnitXAxis", Integer.toString(dotsPerMeter));
          pHYs_node.setAttribute("pixelsPerUnitYAxis", Integer.toString(dotsPerMeter));
          pHYs_node.setAttribute("unitSpecifier", "meter");

          IIOMetadataNode root = new IIOMetadataNode(pngMetadataFormatName);
          root.appendChild(pHYs_node);

          metadata.mergeTree(pngMetadataFormatName, root);

          writer.setOutput(ImageIO.createImageOutputStream(localOutputFile));
          writer.write(metadata, new IIOImage(bufferedImage, null, metadata), writeParam);
          writer.dispose();

          success = true;
      }
      else
      {
          success = ImageIO.write(bufferedImage, formatName, localOutputFile);
      }

      return success;
  }  
}