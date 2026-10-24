import "./App.css";

const PROFILE = "https://github.com/ronrob90";
const REPOSITORY = "https://github.com/ronrob90/Ronrob90/tree/main";
const EMAIL = "RonaldR052390@gmail.com";
const LINKEDIN = "https://www.linkedin.com/in/ronald-roberts-418037152/";

const projects = [
  {
    id: "kalshi", title: "Kalshi BRTI Market Monitor", status: "Read-only research project",
    summary: "A Java application that brings together Bitcoin index readings, contract details, and order-book snapshots, with checks for stale or inconsistent data.",
    tech: ["Java", "Spring Boot", "REST APIs", "WebSockets", "JUnit"],
    problem: "Price sources can disagree, and a quote can become outdated while a contract moves toward settlement. I needed to check which data belonged to the active window and whether it was fresh enough to compare.",
    work: "Combined BRTI readings with market snapshots, added active-contract and timestamp checks, and worked through pagination and API refresh problems. Invalid or ambiguous inputs produce explicit error states.",
    boundary: "The project monitors and analyzes data. It does not execute orders or establish a validated forecasting edge.",
    code: `${REPOSITORY}/projects/kalshi-brti-monitor`, demo: "/demos/kalshi/",
  },
  {
    id: "reading-tracker", title: "Family Reading Tracker", status: "Rebuild in progress",
    summary: "An ongoing React and Spring Boot rebuild of a team capstone, with account registration, a family dashboard, book search, and imports from Open Library.",
    tech: ["Java", "Spring Boot", "React", "JPA", "H2", "Open Library"],
    problem: "Families need a shared place to organize books and follow reading activity. The original capstone brought these workflows together; the rebuild explores a different frontend and persistence layer.",
    work: "The available rebuild uses React, JPA repositories, and a local H2 database. Its backend also includes reading-session, milestone, and prize endpoints. The browser demo covers the dashboard and book-import workflow with fictional data.",
    boundary: "The original Vue/PostgreSQL capstone was collaborative. This rebuild is a local prototype; family-level access controls and the remaining reading-progress screens are unfinished. Credits and scope are documented with the source.",
    code: `${REPOSITORY}/projects/family-reading-tracker`, demo: "/demos/reading-tracker/",
  },
  {
    id: "tenmo", title: "TEnmo", status: "Bootcamp project",
    summary: "A Java payment-service project with a Spring Boot REST API and command-line client, covering accounts, authentication, and transfer workflows.",
    tech: ["Java", "Spring Boot", "PostgreSQL", "JDBC", "JWT"],
    problem: "Model account transfers and transaction states across a client, API, and relational database.",
    work: "Worked with authentication, account management, transfer approval and rejection, JDBC data access, and REST endpoints during Tech Elevator project work.",
    boundary: "An educational payment-service project. The linked overview describes its scope; it is not a hosted financial service.",
    code: `${REPOSITORY}/projects/tenmo`, demo: "",
  },
  {
    id: "futureproof", title: "FutureProof Score", status: "In progress",
    summary: "An exploratory job-resilience scoring API. Current work focuses on the scoring service and response structure, with further model development planned.",
    tech: ["Java", "Spring Boot", "REST API"],
    problem: "Make the assumptions behind a career-resilience score understandable.",
    work: "Built a Spring Boot scoring API as a starting point for an explainable career tool.",
    boundary: "Exploratory project; scores should not be treated as validated predictions of employment outcomes.",
    code: "https://github.com/ronrob90/futureproof-score", demo: "",
  },
];

export default function App() {
  return (
    <div className="page">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="header"><div className="container nav">
        <a className="logo" href="#top" aria-label="Ronald Roberts home">RR</a>
        <nav className="navlinks" aria-label="Main navigation">
          <a href="#projects">Projects</a><a href="#experience">Experience</a><a href="#contact">Contact</a>
        </nav>
      </div></header>
      <main id="main" className="container">
        <section className="hero" id="top">
          <div>
            <p className="eyebrow">Wilmington, Delaware</p>
            <h1>Ronald Roberts</h1>
            <p className="subtitle">Java and Spring Boot developer with an operations background</p>
            <p className="lead">I build practical applications and tools for working with data. My experience in banking and hospital operations shapes how I approach accuracy, troubleshooting, and clear handoffs.</p>
            <div className="ctaRow">
              <a className="btn primary" href="#projects">Explore my work</a>
              <a className="btn" href={PROFILE}>GitHub</a>
              <a className="btn" href={LINKEDIN}>LinkedIn</a>
            </div>
          </div>
          <div className="card hero-feature">
            <p className="eyebrow">Featured project</p><h2>When can you trust a data snapshot?</h2>
            <p className="cardText">The Kalshi monitor checks the active contract, timestamps, and quote consistency before displaying data for analysis.</p>
            <a className="text-link" href="/demos/kalshi/">Try the sample-data demo →</a>
          </div>
        </section>
        <section id="projects" className="section">
          <div className="sectionHead"><h2>Selected projects</h2><p className="muted">The problem, the implementation, and the work still ahead.</p></div>
          <div className="grid">
            {projects.map(p => <article className="card project-card" id={p.id} key={p.id}>
              <div className="cardTop"><h3 className="cardTitle">{p.title}</h3><span className="status warn">{p.status}</span></div>
              <p className="cardText">{p.summary}</p>
              <div className="chips">{p.tech.map(t => <span className="chip" key={t}>{t}</span>)}</div>
              <div className="linksRow project-links">
                {p.demo && <a href={p.demo}>Try sample demo →</a>}
                <a href={p.code}>{p.id === "tenmo" ? "Project overview" : "Source and README"} →</a>
              </div>
              <details className="case-study"><summary>Read the case study</summary>
                <h4>Problem</h4><p>{p.problem}</p><h4>Implementation</h4><p>{p.work}</p><h4>Current scope</h4><p>{p.boundary}</p>
              </details>
            </article>)}
          </div>
        </section>
        <section id="experience" className="section">
          <h2>Experience behind the code</h2>
          <div className="skillsGrid">
            <article className="card"><h3 className="cardTitle">Nemours Children’s Health</h3>
              <p className="muted">Perioperative supply chain and logistics · Apr–Oct 2026</p>
              <p className="cardText">Coordinated operating-room supplies and equipment, built an Excel cart tracker, and documented and escalated routing and handoff issues.</p></article>
            <article className="card"><h3 className="cardTitle">Police and Fire Federal Credit Union</h3>
              <p className="muted">Banking Representative · Aug 2022–Jun 2023</p>
              <p className="cardText">Supported members, resolved account-service concerns, and used CRM workflows and root-cause analysis to address recurring issues.</p></article>
          </div>
          <p className="training">Tech Elevator Java development training · Java, SQL, REST APIs, React, Vue, Git, and JUnit</p>
        </section>
        <section id="contact" className="section"><h2>Let’s connect</h2>
          <div className="card"><p className="text">I’m interested in software development and operations roles where I can improve workflows and solve practical problems.</p>
            <div className="linksRow project-links"><a href={`mailto:${EMAIL}`}>{EMAIL}</a><a href={PROFILE}>GitHub →</a><a href={LINKEDIN}>LinkedIn →</a></div>
          </div>
        </section>
        <footer className="footer">© {new Date().getFullYear()} Ronald Roberts</footer>
      </main>
    </div>
  );
}
