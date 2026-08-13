-- Match the reviewed list exactly. Some imported rules differ only by a trailing
-- slash but represent separate catalog entries with different rule revisions.
UPDATE t_book_source SET enabled = FALSE;

UPDATE t_book_source
SET enabled = TRUE
WHERE source_url IN (
    'https://www.360tingshu.cc',
    'http://www.66story.com',
    'http://m.qudushu.com',
    'http://www.xbotaodz.com',
    'http://www.booksky.cc',
    'https://wap.xyushuwu11.com',
    'http://wap.wangshuge.la',
    'http://www.mxjtedu.com/',
    'https://www.489d.com',
    'https://www.dcrbk.com',
    'https://www.conglianhao.com',
    'https://www.biquge7.top/',
    'https://www.biquge7.top',
    'http://www.bbiquge8.net',
    'https://www.biquge99.cc/',
    'https://www.biquge7.xyz',
    'https://wap.jhssd.com',
    'https://www.jhssd.com',
    'http://wap.wangshuge.info',
    'http://m.xhytd.com'
);
