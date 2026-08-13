-- Platform-curated defaults. Users without a personal preference start with only
-- these validated sources; t_user_book_source_preference can override either way.
UPDATE t_book_source SET enabled = FALSE;

UPDATE t_book_source
SET enabled = TRUE
WHERE RTRIM(source_url, '/') IN (
    'https://www.360tingshu.cc',
    'http://www.66story.com',
    'http://m.qudushu.com',
    'http://www.xbotaodz.com',
    'http://www.booksky.cc',
    'https://wap.xyushuwu11.com',
    'http://wap.wangshuge.la',
    'http://www.mxjtedu.com',
    'https://www.489d.com',
    'https://www.dcrbk.com',
    'https://www.conglianhao.com',
    'https://www.biquge7.top',
    'http://www.bbiquge8.net',
    'https://www.biquge99.cc',
    'https://www.biquge7.xyz',
    'https://wap.jhssd.com',
    'https://www.jhssd.com',
    'http://wap.wangshuge.info',
    'http://m.xhytd.com'
);

COMMENT ON COLUMN t_book_source.enabled IS '平台推荐的默认启用状态；用户偏好存在时由个人设置覆盖';
