#!/usr/bin/env python3
"""CI icin sahte SMTP sunucusu: gelen her e-postayi cozumleyip JSON olarak kaydeder."""
import json
import os
import sys
import time
from email import message_from_bytes, policy

from aiosmtpd.controller import Controller

PORT = int(sys.argv[1])
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)


class Handler:
    async def handle_DATA(self, server, session, envelope):
        msg = message_from_bytes(envelope.content, policy=policy.default)
        part = msg.get_body(preferencelist=("plain", "html"))
        body = part.get_content() if part is not None else ""
        n = len([f for f in os.listdir(OUT) if f.endswith(".json")])
        record = {
            "to": [r.lower() for r in envelope.rcpt_tos],
            "from": envelope.mail_from,
            "subject": str(msg["subject"]),
            "content_type": msg.get_content_type(),
            "body": body,
            "time": time.time(),
        }
        tmp = os.path.join(OUT, "%04d.tmp" % n)
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(record, f, ensure_ascii=False)
        os.replace(tmp, os.path.join(OUT, "%04d.json" % n))
        print("MAIL to=%s subject=%s" % (record["to"], record["subject"]), flush=True)
        return "250 OK"


controller = Controller(Handler(), hostname="127.0.0.1", port=PORT)
controller.start()
print("mail sink listening on %d" % PORT, flush=True)
while True:
    time.sleep(3600)
