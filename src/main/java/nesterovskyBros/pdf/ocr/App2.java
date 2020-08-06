package nesterovskyBros.pdf.ocr;

import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageFilter;
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
      
      //"C:\\temp\\images\\original-image.jpg", 
      //"C:\\temp\\images\\sharpen1.jpg", 

      "C:\\temp\\images\\cheque1.jpg", 
      "C:\\temp\\images\\cheque3.jpg", 
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
      BufferedImage scaledImage = scaleImage(image, 1600);
      
      saveImage(
        scaledImage, 
        "png", 
        new File(folder, "scaled.png"), 
        225);
      
      BufferedImage grayImage = grayImage(scaledImage);

//      BufferedImage blur2Image = blurImage(grayImage, 2);
//
//      saveImage(blur2Image, "png", new File(folder, "blur2.png"), 225);
      
      BackgroundRange backgroundRange = getBackgroundRange(grayImage, .92);
      
      BufferedImage maskedImage = 
        removeWatermarkImage(grayImage, backgroundRange, 3);
     
      saveImage(maskedImage, "png", new File(folder, "masked.png"), 225);
      
//      BufferedImage edgeImage = edgeImage(maskedImage); 

      BufferedImage sharpenedImage = 
        sharpenImage(maskedImage, backgroundRange, 13);

      saveImage(sharpenedImage, "png", new File(folder, "sharpened.png"), 225);
      
      maskedImages[index] = 
        new File(folder, "sharpened.png").getAbsolutePath();
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
  
  static BufferedImage scaleImage(BufferedImage input, int width)
    throws Exception
  {
    int inputWidth = input.getWidth();
    int inputHeight = input.getHeight();
    double scale = 1600.0 / inputWidth;
    int height = (int)(inputHeight * scale);
    
    BufferedImage output = 
      new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    AffineTransform affineTransform = new AffineTransform();
    
    affineTransform.scale(scale, scale);
    
    AffineTransformOp scaleOp = 
     new AffineTransformOp(affineTransform, AffineTransformOp.TYPE_BICUBIC);
    scaleOp.filter(input, output);
    
    return output;
  }

  static BufferedImage grayImage(BufferedImage input)
    throws Exception
  {
    BufferedImage output = new BufferedImage(
      input.getWidth(), 
      input.getHeight(), 
      BufferedImage.TYPE_INT_ARGB);

    ColorConvertOp op = 
      new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);

    op.filter(input, output);
    
    return output;
  }
  
  static BufferedImage blurImage(BufferedImage input, int radius)
    throws Exception
  {
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
  
    return operation.filter(input, null);
  }
  
  static class BackgroundRange
  {
    int background;
    int range;
  }
  
  static BackgroundRange getBackgroundRange(BufferedImage input, double backgroundPercent)
    throws Exception
  {
    int width = input.getWidth();
    int height = input.getHeight();

    // Histogram normalization.
    int count = width * height;
    int backgroundSize =  (int)(count * backgroundPercent);
    int[] histogram = new int[256];
    int[] pixels = new int[width];

    // Read pixel intensities into histogram.
    for(int y = 0; y < height; y++) 
    {
      input.getRGB(0, y, width, 1, pixels, 0, width);
      
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
      
      if (!hasRange || 
        (Math.abs(backgroundSize - size) > 
          Math.abs(backgroundSize - histogramBackgroundSize)))
      {
        break;
      }
      
      ++backgroundRange;
      histogramBackgroundSize = size;
    }

//    long sum =0;
//    // build a Lookup table LUT containing scale factor
//    int[] lut = new int[256];
//    
//    for(int i = 0; i < 256; ++i )
//    {
//      sum += histogram[i];
//      lut[i] = (int)(sum * 255.0f / count);
//    }
//
//    // transform image using sum histogram as a Lookup table
//    for(int y = 0; y < height; y++) 
//    {
//      input.getRGB(0, y, width, 1, pixels, 0, width);
//      
//      for(int i = 0; i < pixels.length; ++i)
//      {
//        int before = pixels[i] & 0xff;
//        int after = lut[before];
//        
//        pixels[i] = 0xff000000 | after | (after << 8) | (after << 16);
//      }
//
//      input.setRGB(0, y, width, 1, pixels, 0, width);
//    }
    
    BackgroundRange result = new BackgroundRange();
    
    result.background = histogramMax;
    result.range = backgroundRange;
    
    return result; 
  }
  
  static BufferedImage removeWatermarkImage(
    BufferedImage input,
    BackgroundRange backgroundRange,
    int maskSize)
    throws Exception
  {
    int width = input.getWidth();
    int height = input.getHeight();
    int background = backgroundRange.background;
    int range = backgroundRange.range;
    int[] pixels = new int[maskSize * maskSize];

    ImageFilter maskFilter0 = new RGBImageFilter()
    {
      @Override
      public int filterRGB(int x, int y, int rgb)
      {
        rgb = rgb & 0xff;
        
        int value;
        int s1 = 0;
//        int s2 = 0;
        int count = 0;
        int m = maskSize / 2;

        for(int i = 0; i < maskSize; i++)
        {
          for(int j = 0; j < maskSize; j++)
          {
            int px = x - m + j;
            int py = y - m + i;
            
            if ((px >= 0) && (px < width) && (py >= 0) && (py < height))
            {
              value = input.getRGB(px, py) & 0xff;
              pixels[i * maskSize + j] = value;
              value = Math.abs(value - background) > range ? 
                value : background;
              
              ++count;
              s1 += value;
//              s2 += value * value;
            }
          }
        }

        if (count == 0)
        {
          value = background;
        }
        else
        {
//          if (maskSize >= 3)
//          {
//            rgb = (pixels[(m - 1) * maskSize + m - 1] +
//              pixels[(m - 1) * maskSize + m] * 2 +
//              pixels[(m - 1) * maskSize + m + 1] +
//              pixels[m * maskSize + m - 1] * 2 +
//              pixels[m * maskSize + m] * 4 +
//              pixels[m * maskSize + m + 1] * 2 +
//              pixels[(m + 1) * maskSize + m - 1] +
//              pixels[(m + 1) * maskSize + m] * 2 +
//              pixels[(m + 1) * maskSize + m + 1]) / 16;
//          }
          
          double mean = (double)s1 / count;
//          double stdev = Math.sqrt(s2 * count - s1 * s1) / count;
          
          value = (int)mean;

          value = (Math.abs(value - background) > range) &&
            Math.abs(rgb - background) > range ? rgb : background;
        }
                  
        return 0xff000000 | value | (value << 8) | (value << 16);
      }
    };
    
    PixelGrabber grabber = new PixelGrabber(
      new FilteredImageSource(input.getSource(), maskFilter0), 
      0, 
      0, 
      -1, 
      -1, 
      null, 
      0,
      0);
    
    grabber.grabPixels();

    BufferedImage output = 
      new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    
    output.setRGB(
      0, 
      0, 
      width, 
      height,
      (int[])grabber.getPixels(), 
      0, 
      grabber.getWidth());
   
    return output;
  }
  
  static BufferedImage sharpenImage(
    BufferedImage input,
    BackgroundRange backgroundRange,
    int maskSize)
    throws Exception
  {
    int width = input.getWidth();
    int height = input.getHeight();
    int background = backgroundRange.background;
    int range = backgroundRange.range;
    int[] pixels = new int[maskSize * maskSize];
    
    ImageFilter maskFilter = new RGBImageFilter()
    {
      @Override
      public int filterRGB(int x, int y, int rgb)
      {
        rgb = rgb & 0xff;
        rgb = (Math.abs(rgb - background) > range) ? rgb : background;

        int value;

        int s1 = 0;
        int s2 = 0;
        int count = 0;

        for(int i = 0; i < maskSize; i++)
        {
          for(int j = 0; j < maskSize; j++)
          {
            int px = x - maskSize / 2 + j;
            int py = y - maskSize / 2 + i;
            
            if ((px >= 0) && (px < width) && (py >= 0) && (py < height))
            {
              value = input.getRGB(px, py) & 0xff;
              value = (Math.abs(value - background) > range) ? 
                value : background;
              pixels[i * maskSize + j] = value;
              
              ++count;
              s1 += value;
              s2 += value * value;
            }
          }
        }
        
        if (count <= 1)
        {
          value = rgb;
        }
        else
        {
          double mean = (double)s1 / count;
          double stdev = Math.sqrt(s2 * count - s1 * s1) / count;
          
          int y0 = 0;
          int m = maskSize / 2;
          
          
          for(int i = 0; i < maskSize; i++)
          {
            for(int j = 0; j < maskSize; j++)
            {
              int px = x - m + j;
              int py = y - m + i;
              
              if ((px >= 0) && (px < width) && (py >= 0) && (py < height))
              {
                value = pixels[i * maskSize + j];
                
                if (value <= mean)
                {
                  ++y0;
                }
              }
            }
          }
          
          value = rgb <= mean ? 
            (int)Math.max(
              mean - stdev * Math.sqrt((count - y0) / (double)y0), 
              0) : 
            (int)Math.min(
              mean + stdev * Math.sqrt((double)y0 / (count - y0)), 
              256);

          value = (Math.abs(value - background) > range) ? value : background;
        }
                  
        return 0xff000000 | value | (value << 8) | (value << 16);
      }
    };
    
    PixelGrabber grabber = new PixelGrabber(
      new FilteredImageSource(input.getSource(), maskFilter), 
      0, 
      0, 
      -1, 
      -1, 
      null, 
      0,
      0);
    
    grabber.grabPixels();

    BufferedImage output = 
      new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    
    output.setRGB(
      0, 
      0, 
      width, 
      height, 
      (int[])grabber.getPixels(), 
      0, 
      grabber.getWidth());
    
    return output;
  }

  static BufferedImage edgeImage(BufferedImage input)
    throws Exception
  {
    int width = input.getWidth();
    int height = input.getHeight();
    int[][] edgeColors = new int[width][height];
    int maxGradient = -1;

    for(int y = 1; y < height - 1; y++) 
    {
      for(int x = 1; x < width - 1; x++)
      {
        int val00 = input.getRGB(x - 1, y - 1) & 0xff;
        int val01 = input.getRGB(x - 1, y) & 0xff;
        int val02 = input.getRGB(x - 1, y + 1) & 0xff;

        int val10 = input.getRGB(x, y - 1) & 0xff;
        int val11 = input.getRGB(x, y) & 0xff;
        int val12 = input.getRGB(x, y + 1) & 0xff;

        int val20 = input.getRGB(x + 1, y - 1) & 0xff;
        int val21 = input.getRGB(x + 1, y) & 0xff;
        int val22 = input.getRGB(x + 1, y + 1) & 0xff;

        int gx =  ((-1 * val00) + (0 * val01) + (1 * val02)) 
                + ((-2 * val10) + (0 * val11) + (2 * val12))
                + ((-1 * val20) + (0 * val21) + (1 * val22));

        int gy =  ((-1 * val00) + (-2 * val01) + (-1 * val02))
                + ((0 * val10) + (0 * val11) + (0 * val12))
                + ((1 * val20) + (2 * val21) + (1 * val22));

        double gval = Math.sqrt((gx * gx) + (gy * gy));
        int g = (int) gval;

        if(maxGradient < g) 
        {
          maxGradient = g;
        }

        edgeColors[x][y] = g;
      }
    }

    double scale = 255.0 / maxGradient;

    BufferedImage output = 
      new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

    for(int y = 0; y < height; y++) 
    {
      for(int x = 0; x < width; x++) 
      {
        int edgeColor = edgeColors[x][y];
        //int maskColor = input.getRGB(x, y) & 0xff;
        
        edgeColor = (int)(edgeColor * scale);
        //edgeColor = (int)((1.0 - (double)edgeColor / maxGradient) * maskColor); 
        edgeColor = 
          0xff000000 | (edgeColor << 16) | (edgeColor << 8) | edgeColor;

        output.setRGB(x, y, edgeColor);
      }
    }
    
    return output;
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