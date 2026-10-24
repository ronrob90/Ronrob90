import "./App.css";
import { projects } from "./content/projects";
import { ProjectCard } from "./components/ProjectCard";

const PROFILE = "https://github.com/ronrob90";
const EMAIL = "RonaldR052390@gmail.com";
const LINKEDIN = "https://www.linkedin.com/in/ronald-roberts-418037152/";

export default function App() {
  return (
    <div className="page">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="header">
        <div className="container nav">
          <a className="logo" href="#top" aria-label="Ronald Roberts home">
            RR
          </a>
          <nav className="navlinks" aria-label="Main navigation">
            <a href="#projects">Projects</a>
            <a href="#contact">Contact</a>
          </nav>
        </div>
      </header>
      <main id="main" className="container">
        <section className="hero" id="top">
          <div>
            <p className="eyebrow">Wilmington, Delaware</p>
            <h1>Ronald Roberts</h1>
            <p className="subtitle">Java and Spring Boot developer</p>
            <p className="lead">
              I build practical applications and tools for working with data. Explore my projects,
              try the demos, and see how I approach API integration, data validation, and clear
              workflows.
            </p>
            <div className="ctaRow">
              <a className="btn primary" href="#projects">
                Explore my work
              </a>
              <a className="btn" href={PROFILE}>
                GitHub
              </a>
              <a className="btn" href={LINKEDIN}>
                LinkedIn
              </a>
            </div>
          </div>
          <div className="card hero-feature">
            <p className="eyebrow">Featured project</p>
            <h2>When can you trust a data snapshot?</h2>
            <p className="cardText">
              The Kalshi monitor checks the active contract, timestamps, and quote consistency
              before displaying data for analysis.
            </p>
            <a className="text-link" href="/demos/kalshi/">
              Try the sample-data demo →
            </a>
          </div>
        </section>
        <section id="projects" className="section">
          <div className="sectionHead">
            <h2>Selected projects</h2>
            <p className="muted">The problem, the implementation, and the work still ahead.</p>
          </div>
          <div className="grid">
            {projects.map((project) => (
              <ProjectCard project={project} key={project.id} />
            ))}
          </div>
        </section>
        <section id="contact" className="section">
          <h2>Let’s connect</h2>
          <div className="card">
            <p className="text">
              Have a question about a project or an idea for a collaboration? Get in touch.
            </p>
            <div className="linksRow project-links">
              <a href={`mailto:${EMAIL}`}>{EMAIL}</a>
              <a href={PROFILE}>GitHub →</a>
              <a href={LINKEDIN}>LinkedIn →</a>
            </div>
          </div>
        </section>
        <footer className="footer">© {new Date().getFullYear()} Ronald Roberts</footer>
      </main>
    </div>
  );
}
