FROM clamav/clamav:1.5.2
RUN freshclam
ENTRYPOINT ["clamscan", "--recursive"]
