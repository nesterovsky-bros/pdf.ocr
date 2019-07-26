  <p>We were asked to help with search service in one enterprise. We were told that their SharePoint portal does not serve their need. Main complaints were about the quality of search results.</p>
  <p>They have decided to implement external index of SharePoint content, using <a href="https://www.elastic.co/">Elastic</a>, and expose custom search API within the enterprise.</p>
  <p></p>
  <p>We questioned their conclusions, asked why did they think Elastic will give much better results, asked did they try to figure out why SharePoint give no desired results.</p>
  <p>Answers did not convince us though we have joined the project.</p>
  <p></p>
  <p>
    What do you think? 
    Elastic did not help at all though they hoped very much that its query language will help to rank results in a way that matched documents will be found.
    After all they thought it was a problem of ranking of results.</p>
  <p></p>
  <p>
    Here we have started our analysis. We took a specific document that must be found but is never returned from search.</p>
  <p></p>
  <p>
    It turned to be well known problem, at least we dealt with closely related one in the past. There are two ingredients here:</p>
  <ul>
    <li>documents that have low chances to be found are PDFs;</li>
    <li>we live in Israel, so most texts are Hebrew, which means words are written from right to left, while some other texts from left to right. See <a href="https://en.wikipedia.org/wiki/Bi-directional_text" target="_blank">Bi-directional text</a>.</li>
  </ul>
  <p>
    Traditionally PDF documents are provided in a way that only distantly resembles logical structure of original content. E.g., paragraphs of texts are often represented as unrelated runs of text lines, or as set of text runs representing single words, or independant characters. No need to say that additional complication comes from that Hebrew text are often represented visually (from left to right, as if &quot;hello&quot; would be stored as &quot;olleh&quot; and would be just printed from right to left). Another common feature of PDF are custom fonts with uncanonical mappings, or images with glyphs of letters.</p>
  <p>
    You can implement these tricks in other document formats but for some reason PDF is only format we have seen that regularly and intensively uses these techniques.</p>
  <p></p>
  <p>
    At this point we have realized that it&#39;s not a fault of a search engine to find the document but the feature of the PDF to expose its text to a crawler in a form that cannot be used for search.
    In fact, PDF cannot search by itself in such documents, as when you try to find some text in the document opened in a pdf viewer, that you clearly see in the document, you often find nothing.</p>
  <p></p>
  <p>
    A question. What should you do in this case when no any PDF text extractor can give you correct text but text is there when you looking at document in a pdf viewer?</p>
  <p></p>
  <p>
    We decided it&#39;s time to go in the direction of image recognition. Thankfully, nowdays it&#39;s a matter of available processing resources.</p>
  <p>
    Our goal was:</p>
  <ol>
    <li>Have images of each PDF page. This task is immediately solved with <a href="https://pdfbox.apache.org/" target="_blank">Apache PDFBox (A Java PDF Library) - it's time to say this is java project.</a></li>
    <li>Run Optical Character Recognition (OCR) over images, and get extracted texts. This is perfectly done by <a href="https://github.com/tesseract-ocr/tesseract" target="_blank">tesseract-ocr/tesseract</a>, and thankfully to its java wrapper <a href="https://github.com/bytedeco/javacpp-presets/tree/master/tesseract" target="_blank">bytedeco/javacpp-presets</a> we can immediately call this C++ API from java.</li>
  </ol>
  <p>
    The only small nuisance of tesseract is that it does not expose table recognition info, but we can easily overcome it (we solved this task in the past), as along with each text run tesseract exposes its position.</p>

<p></p>
  <p>What are results of the run of such program?</p>
  <ol>
    <li>Full success! It works with high quality of recognition. Indeed, there is no any physical noise that impacts quality.</li>
    <li>Slow speed - up to several seconds per recognition per  page.</li>
    <li>Scalable solution. Slow speed can be compensated by almost unlimited theoretical scalability.</li>
  </ol>
  <p></p>
  <p>So, what is the lesson we have taked from this experience?</p>
  <p></p>
  <p>Well, you should question yourself, test and verify ideas on the ground before building any theories that will lead you in completely wrong direction. After all people started to realize there was no need to claim on SharePoint, to throw it, and to spend great deal of time and money just to prove that the problem is in the different place.</p>

  <p>A sample source code can be found at <a href="https://github.com/nesterovsky-bros/pdf.ocr/blob/master/src/main/java/nesterovskyBros/pdf/ocr/App.java" target="_blank">App.java</a></p>

