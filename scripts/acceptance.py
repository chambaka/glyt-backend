#!/usr/bin/env python3
"""Walk the KIA → Zanzibar pilot path against a running GoLyt API."""

import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
JPEG = bytes.fromhex(
    "ffd8ffe000104a46494600010100000100010000ffdb004300080606070605080707070909080a0c140d0c0b0b0c1912130f141d1a1f1e1d1a1c1c20242e2720222c231c1c2837292c30313434341f27393d38323c2e333432ffc0000b080001000101011100ffc40014100100000000000000000000000000000000ffda00080001000100003f00fbffd9"
)


class Client:
    def __init__(self, token=None):
        self.token = token

    def call(self, method, path, body=None, expected=200):
        data = None if body is None else json.dumps(body).encode()
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
                status = response.status
                payload = json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            status = error.code
            payload = json.loads(raw) if raw else None
        if status != expected:
            raise SystemExit(f"{method} {path} -> {status} {payload}, expected {expected}")
        return payload

    def upload(self, bag_id, stage):
        boundary = "----golytboundary"
        head = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="bag.jpg"\r\n'
            f"Content-Type: image/jpeg\r\n\r\n"
        ).encode()
        tail = f"\r\n--{boundary}--\r\n".encode()
        request = urllib.request.Request(
            f"{BASE}/api/v1/bags/{bag_id}/photos?stage={stage}",
            data=head + JPEG + tail,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": f"multipart/form-data; boundary={boundary}",
            },
            method="POST",
        )
        with urllib.request.urlopen(request) as response:
            if response.status != 200:
                raise SystemExit(f"upload {stage} failed")


def login(email):
    payload = Client().call("POST", "/api/v1/auth/login", {"email": email, "password": "Golyt#2026"})
    return Client(payload["token"])


def make_bag(api, name, phone, weight=18):
    person = api.call("POST", "/api/v1/customers", {
        "fullName": name,
        "phone": phone,
        "email": phone + "@example.com",
        "nationality": "TZ",
        "passportNumber": "AB" + phone[-4:],
    })
    booking = api.call("POST", "/api/v1/bookings", {
        "customerId": person["id"],
        "internationalArrivalFlight": "KQ100",
        "kiaArrivalDate": "2026-09-27",
        "zanzibarTravelDate": "2026-09-28",
        "zanzibarFlight": "PW401",
    })
    bag = api.call("POST", "/api/v1/bags", {"bookingId": booking["id"], "bag": {
        "bagType": "SUITCASE", "color": "Navy", "weightKg": weight, "description": "Soft suitcase", "originalAirlineTag": "117"
    }})
    assert bag["code"].startswith("BG-")
    assert bag["status"] == "BOOKED"
    api.upload(bag["id"], "KIA_FULL")
    api.upload(bag["id"], "KIA_TAG")
    bag = api.call("POST", f"/api/v1/bags/{bag['id']}/receive", {
        "weightKg": weight, "condition": "GOOD", "prohibitedConfirmed": True, "damageNote": ""
    })
    assert bag["status"] == "RECEIVED_KIA"
    api.upload(bag["id"], "KIA_SEAL")
    bag = api.call("POST", f"/api/v1/bags/{bag['id']}/seal", {
        "sealNumber": "TS" + str(bag["id"]).zfill(4), "condition": "GOOD", "acceptOverweight": False, "damageNote": ""
    })
    assert bag["status"] == "SEALED"
    assert bag["sealNumber"]
    return bag


def main():
    kia = login("kia@golyt.co.tz")
    first = make_bag(kia, "Asha Mwinyi", "255700111001")
    second = make_bag(kia, "Juma Said", "255700111002", 16)
    manifest = kia.call("POST", "/api/v1/manifests", {
        "airline": "Precision Air", "flightNumber": "PW401", "manifestDate": "2026-09-28", "route": "JRO-ZNZ", "notes": "Pilot"
    })
    assert manifest["number"].startswith("MF-JRO-ZNZ-")
    manifest = kia.call("POST", f"/api/v1/manifests/{manifest['id']}/bags", {"bagIds": [first["id"], second["id"]]})
    assert manifest["bagCount"] == 2
    manifest = kia.call("POST", f"/api/v1/manifests/{manifest['id']}/finalise")
    assert manifest["status"] == "FINALISED"
    manifest = kia.call("POST", f"/api/v1/manifests/{manifest['id']}/handover")
    assert manifest["status"] == "HANDED_OVER"
    first = kia.call("GET", f"/api/v1/bags/{first['id']}")
    assert first["status"] == "HANDED_AIRLINE"
    assert len(first["custody"]) >= 3
    assert len(first["audits"]) >= 3

    znz = login("znz@golyt.co.tz")
    znz.upload(first["id"], "ZNZ_CONDITION")
    znz.upload(first["id"], "ZNZ_SEAL")
    first = znz.call("POST", f"/api/v1/bags/{first['id']}/receive-znz", {
        "sealObserved": first["sealNumber"], "sealIntact": True, "sealDamaged": False, "condition": "GOOD", "exceptionNote": ""
    })
    assert first["status"] == "RECEIVED_ZNZ"
    first = znz.call("POST", f"/api/v1/bags/{first['id']}/store", {"storageCode": "A-01"})
    assert first["status"] == "STORED"
    assert first["storageCode"] == "A-01"
    znz.call("POST", f"/api/v1/bags/{first['id']}/release", {
        "collectedBy": "Asha", "idVerified": True, "notes": "", "confirmed": True
    }, expected=400)

    page = urllib.request.urlopen(first["trackingUrl"])
    html = page.read().decode()
    assert "Secured in Zanzibar" in html
    assert "passport" not in html.lower()

    opened = znz.call("POST", "/api/v1/exceptions", {
        "bagId": first["id"], "type": "DELAYED", "description": "Customer called ahead", "priority": "NORMAL"
    })
    resolved = znz.call("POST", f"/api/v1/exceptions/{opened['id']}/resolve", {"notes": "Customer confirmed the pickup time"})
    assert resolved["status"] == "RESOLVED"

    first = znz.call("POST", f"/api/v1/bags/{first['id']}/ready")
    assert first["status"] == "READY"
    first = znz.call("POST", f"/api/v1/bags/{first['id']}/release", {
        "collectedBy": "Asha Mwinyi", "idVerified": True, "notes": "Passport checked", "confirmed": True
    })
    assert first["status"] == "COLLECTED"
    board = znz.call("GET", "/api/v1/storage")
    slot = next(item for item in board if item["code"] == "A-01")
    assert slot["status"] == "Available"

    znz.upload(second["id"], "ZNZ_CONDITION")
    znz.upload(second["id"], "ZNZ_SEAL")
    mismatch = znz.call("POST", f"/api/v1/bags/{second['id']}/receive-znz", {
        "sealObserved": "WRONG", "sealIntact": False, "sealDamaged": True, "condition": "DAMAGED", "exceptionNote": "Seal does not match"
    })
    assert mismatch["status"] == "RECEIVED_ZNZ"
    assert mismatch["exceptionState"] == "OPEN"
    assert any(item["type"] == "SEAL_MISMATCH" for item in mismatch["exceptions"])

    kia.call("POST", f"/api/v1/bags/{first['id']}/release", {
        "collectedBy": "Asha", "idVerified": True, "notes": "", "confirmed": True
    }, expected=403)
    login("board@golyt.co.tz").call("POST", "/api/v1/bags", {"bookingId": first["customer"]["bookingId"], "bag": {
        "bagType": "SUITCASE", "color": "Blue", "weightKg": 10, "description": "No", "originalAirlineTag": ""
    }}, expected=403)
    qr = urllib.request.Request(BASE + f"/api/v1/bags/{second['id']}/qr", headers={"Authorization": f"Bearer {kia.token}"})
    with urllib.request.urlopen(qr) as response:
        body = response.read()
        assert response.status == 200 and body.startswith(b"\x89PNG")
    pdf = urllib.request.Request(BASE + f"/api/v1/manifests/{manifest['id']}/pdf", headers={"Authorization": f"Bearer {kia.token}"})
    with urllib.request.urlopen(pdf) as response:
        body = response.read()
        assert response.status == 200 and body.startswith(b"%PDF")
    scanned = kia.call("POST", "/api/v1/qr/scan", {"code": "golyt:" + second["code"]})
    assert scanned["id"] == second["id"]
    print("Acceptance flow passed.")


if __name__ == "__main__":
    main()
