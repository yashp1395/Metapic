# 📸 Metapic - Smart Face Matching for Events

Welcome to **Metapic**! This project is a smart photo sharing platform that uses **Face Recognition** to help people find their photos from events (like weddings, parties, or conferences) instantly.

---

## 👨‍💻 Author
**Yash Patil**

---

## 🧐 What This Project Does

Imagine you go to a wedding. The photographer takes thousands of photos. Instead of scrolling through all of them to find yourself, you simply:
1.  **Upload a selfie.**
2.  **The system scans all event photos.**
3.  **It magically shows you ONLY the photos you are in!**

It saves time and makes sharing event photos super easy and private.

---

## ⚙️ How It Works (In Simple Words)

1.  **Photographer Uploads**: A photographer creates a group (e.g., "John's Wedding") and bulk uploads all the event photos.
2.  **Face Scanning**: The system (Face Service) looks at every photo, finds faces, and creates a unique digital "fingerprint" (a 512-dimensional embedding) for each face using InsightFace.
3.  **Client Search**: A guest visits the site, enters the event code, and uploads a single selfie.
4.  **Matching**: The Java Backend compares the guest's selfie "fingerprint" with all the stored face fingerprints using a Cosine Similarity algorithm.
5.  **Results**: If there's a match, that photo is instantly returned to the guest.

---

## 📂 Folder Structure

The project is built on a decoupled microservices architecture:

-   **`frontend/`** (The User Interface)
    -   Built with **React**, **Vite**, and **Tailwind CSS**.
    -   Handles fast user interactions, image compression, and dashboards.
-   **`backend-java/`** (The Core Gateway API)
    -   Built with **Java 21** and **Spring Boot**.
    -   Handles JWT authentication, rate limiting, MongoDB operations, and orchestrates requests between the frontend and the AI service.
-   **`face-service/`** (The AI Power)
    -   Built with **Python** and **FastAPI**.
    -   Uses **InsightFace** and ONNX Runtime to perform heavy facial detection and vector mapping.

---

## 🚀 How to Run It Locally (Step-by-Step)

For detailed running instructions, please see the `run.md` and `project_info.md` files. Below is the quick-start guide:

### Prerequisites
-   Java 21 and Maven
-   Python 3.10+
-   Node.js (for React)
-   MongoDB running locally on port 27017
-   A [Cloudinary](https://cloudinary.com/) account (for storing images).

### 1. Database & AI Service (Terminal 1)
```bash
cd face-service
python -m venv venv
source venv/Scripts/activate   # (Windows)
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 2. Java Backend (Terminal 2)
```bash
cd backend-java
# Copy .env.example to .env and insert your Cloudinary keys!
mvn spring-boot:run
```
*(The backend will start on http://localhost:4000)*

### 3. React Frontend (Terminal 3)
```bash
cd frontend
npm install
npm run dev
```
*(The frontend will start on http://localhost:5173)*

---

## 🛠️ Tech Stack

-   **Frontend**: React, Vite, Tailwind CSS
-   **Core Backend**: Java 21, Spring Boot 3.4.3
-   **AI Microservice**: Python, FastAPI, InsightFace
-   **Database**: MongoDB (Spring Data MongoDB)
-   **Image Storage**: Cloudinary CDN
-   **Security**: JWT, BCrypt, Token-Bucket Rate Limiting

---

## 💡 Real-World Use Cases

-   **Weddings**: Guests get their own photos without pestering the couple.
-   **Marathons/Sports**: Runners find their race photos using their face.
-   **Conferences**: Attendees get their networking photos instantly.
-   **School Events**: Parents find photos of their specific child.

---

## ⚠️ Limitations & Future Improvements

**Current Limitations:**
-   **Accuracy**: Face recognition isn't 100% perfect. Blurry photos or side profiles might be missed.
-   **Synchronous Processing**: Very large batch uploads currently wait on the AI service to finish processing before returning a response.

**Future Improvements:**
-   [ ] Asynchronous message queue (RabbitMQ/Kafka) for background AI processing.
-   [ ] Migrate to a dedicated Vector Database (e.g., Pinecone) for faster similarity searches.
-   [ ] Add support for video face matching.
