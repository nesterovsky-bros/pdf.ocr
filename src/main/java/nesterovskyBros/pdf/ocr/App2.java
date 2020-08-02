package nesterovskyBros.pdf.ocr;

import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
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
      File folder = new File(path.substring(0, path.lastIndexOf('.')));
      
      folder.mkdirs();
      
      BufferedImage image = ImageIO.read(file);
      int imageWidth = image.getWidth();
      int imageHeight = image.getHeight();
      double scale = 1600.0 / imageWidth;
      int width = 1600;
      int height = (int)(imageHeight * scale);
      
      BufferedImage scaledImage = 
        new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      AffineTransform affineTransform = new AffineTransform();
      
      affineTransform.scale(scale, scale);
      
      AffineTransformOp scaleOp = 
       new AffineTransformOp(affineTransform, AffineTransformOp.TYPE_BICUBIC);
      scaleOp.filter(image, scaledImage);
      
      saveImage(
        scaledImage, 
        "png", 
        new File(folder, "scaled.png"), 
        225);
      
      BufferedImage grayImage = new BufferedImage(
        width, 
        height, 
        BufferedImage.TYPE_INT_ARGB);

      ColorConvertOp op = 
        new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

      op.filter(scaledImage, grayImage);
      
      saveImage(
        grayImage, 
        "png", 
        new File(folder, "gray.png"), 
        225);
      
//      BufferedImage blur2Image;
//      
//      {
//        int radius = 2;
//        int size = radius * 2 + 1;
//        float weight = 1.0f / (size * size);
//        float[] data = new float[size * size];
//  
//        for(int i = 0; i < data.length; i++)
//        {
//          data[i] = weight;
//        }
//        
//        Kernel kernel = new Kernel(size, size, data);
//        ConvolveOp operation = 
//          new ConvolveOp(kernel, ConvolveOp.EDGE_ZERO_FILL, null);
//  
//        blur2Image = operation.filter(grayImage, null);
//        
//        saveImage(
//          blur2Image, 
//          "png", 
//          new File(folder, "blur2.png"), 
//          225);
//      }      
      
//      BufferedImage blur4Image;
//      
//      {
//        int radius = 3;
//        int size = radius * 2 + 1;
//        float weight = 1.0f / (size * size);
//        float[] data = new float[size * size];
//  
//        for(int i = 0; i < data.length; i++)
//        {
//          data[i] = weight;
//        }
//        
//        Kernel kernel = new Kernel(size, size, data);
//        ConvolveOp operation = 
//          new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
//  
//        blur4Image = operation.filter(grayImage, null);
//        
//        saveImage(
//          blur4Image, 
//          "png", 
//          new File(folder, "blur4.png"), 
//          225);
//      }
      
      BufferedImage filterImage = grayImage;

      // Histogram normalization.
      double backgroundPercent = .92;
      int count = width * height;
      int backgroundSize =  (int)(count * backgroundPercent);
      int[] histogram = new int[256];
      int[] pixels = new int[width];

      // Read pixel intensities into histogram.
      for(int y = 0; y < height; y++) 
      {
        filterImage.getRGB(0, y, width, 1, pixels, 0, width);
        
        for(int pixel: pixels)
        {
          ++histogram[pixel & 0xff];
        }
      }
      
      int histogramMax = 0;
      int c = histogram[0];
      
      for(int i = 1; i < 256; ++i)
      {
        int ic = histogram[i];
        
        if (c < ic)
        {
          c = ic;
          histogramMax = i;
        }
      }
      
      int backgroundRange = 1;
      int histogramBackgroundSize = histogram[histogramMax];
      
      for(int i = 1; i < 256; ++i)
      {
        int size = histogramBackgroundSize;
        boolean hasRange = false;
        
        if (histogramMax + i < 256)
        {
          hasRange = true;
          size += histogram[histogramMax + i];
        }

        if (histogramMax - i >= 0)
        {
          hasRange = true;
          size += histogram[histogramMax - i];
        }
        
        if (!hasRange || (size > backgroundSize))
        {
          break;
        }
        
        ++backgroundRange;
        histogramBackgroundSize = size;
      }

//      long sum =0;
//      // build a Lookup table LUT containing scale factor
//      int[] lut = new int[256];
//      
//      for(int i = 0; i < 256; ++i )
//      {
//        sum += histogram[i];
//        lut[i] = (int)(sum * 255.0f / count);
//      }
//
//      // transform image using sum histogram as a Lookup table
//      for(int y = 0; y < height; y++) 
//      {
//        grayImage.getRGB(0, y, width, 1, pixels, 0, width);
//        
//        for(int i = 0; i < pixels.length; ++i)
//        {
//          int before = pixels[i] & 0xff;
//          int after = lut[before];
//          
//          pixels[i] = 0xff000000 | after | (after << 8) | (after << 16);
//        }
//
//        grayImage.setRGB(0, y, width, 1, pixels, 0, width);
//      }
//
//      ImageIO.write(
//        grayImage, 
//        "png",
//        new File(
//          folder, 
//          "normalized-" + name.substring(0, name.lastIndexOf('.')) + ".png"));
      
      int background = histogramMax;
      int range = backgroundRange;

      //BufferedImage maskImage = filterImage;//blur4Image;
      int maskSize = 4;

      pixels = new int[maskSize * maskSize];

      ImageFilter maskFilter = new RGBImageFilter()
      {

        @Override
        public int filterRGB(int x, int y, int rgb)
        {
          int value;
//          int minValue = 255;
//          int maxValue = 0;
          int s1 = 0;
//          int s2 = 0;
          int count = 0;

          for(int i = 0; i < maskSize; i++)
          {
            for(int j = 0; j < maskSize; j++)
            {
              int px = x - maskSize / 2 + j;
              int py = y - maskSize / 2 + i;
              
              if ((px >= 0) && (px < width) && (py >= 0) && (py < height))
              {
                rgb = filterImage.getRGB(px, py) & 0xff;
                value = Math.abs(rgb - background) > range ? rgb : background;
                
                ++count;
                s1 += value;
//                s2 += value * value;
//                
//                if (minValue > value)
//                {
//                  minValue = value;
//                }
//
//                if (maxValue < value)
//                {
//                  maxValue = value;
//                }
              }
            }
          }
          
          if (count == 0)
          {
            value = background;
          }
          else
          {
            int mean = (int)((double)s1 / count);
            //int stdev = (int)(Math.sqrt(s2 * count - s1 * s1) / count);
            
            value = mean;
            value = Math.abs(value - background) > range ? value : background;
          }
                    
          return 0xff000000 | value | (value << 8) | (value << 16);
        }
      };
      
      PixelGrabber grabber = new PixelGrabber(
        new FilteredImageSource(filterImage.getSource(), maskFilter), 
        0, 
        0, 
        -1, 
        -1, 
        null, 
        0,
        0);
      
      grabber.grabPixels();

      BufferedImage maskedImage = 
        new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      
      maskedImage.setRGB(
        0, 
        0, 
        width, 
        height,
        (int[])grabber.getPixels(), 
        0, 
        grabber.getWidth());
      
      File maskedFile = new File(folder, "masked.png");
      
      saveImage(maskedImage, "png", maskedFile, 225);

      maskedImages[index] = maskedFile.getAbsolutePath();
    }

//    if (true)
//    {
//      return;
//    }
    
//    maskedImages = new String[]
//    {
////      "C:\\temp\\images\\cheque1.jpg",
////      "C:\\temp\\images\\gray-cheque1.png",
////      "C:\\temp\\images\\blured-cheque1.png",
//      "C:\\temp\\images\\masked-cheque1.png"
//    };
    
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